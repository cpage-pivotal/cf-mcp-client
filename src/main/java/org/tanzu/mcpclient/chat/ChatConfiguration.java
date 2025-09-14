package org.tanzu.mcpclient.chat;

import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.tanzu.mcpclient.metrics.McpServer;
import org.tanzu.mcpclient.model.ModelDiscoveryService;
import org.tanzu.mcpclient.mcp.McpClientFactory;
import org.tanzu.mcpclient.mcp.McpDiscoveryService;
import org.tanzu.mcpclient.mcp.McpServerService;
import org.tanzu.mcpclient.mcp.ProtocolType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Configuration
public class ChatConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ChatConfiguration.class);

    private final String chatModel;
    private final List<String> agentServices;
    private final List<String> allMcpServiceURLs;
    private final List<McpServer> mcpServersWithHealth;
    private final List<String> healthyMcpServiceURLs;
    private final ApplicationEventPublisher eventPublisher;
    private final McpClientFactory mcpClientFactory;
    private final McpDiscoveryService mcpDiscoveryService;

    // Map to store server names by URL for use by other services
    private final Map<String, String> serverNamesByUrl = new ConcurrentHashMap<>();
    // Protocol-aware MCP server services
    private final List<McpServerService> mcpServerServices = new ArrayList<>();

    public ChatConfiguration(ModelDiscoveryService modelDiscoveryService, McpDiscoveryService mcpDiscoveryService,
                             ApplicationEventPublisher eventPublisher, McpClientFactory mcpClientFactory) {
        this.chatModel = modelDiscoveryService.getChatModelName();
        this.mcpDiscoveryService = mcpDiscoveryService;
        this.agentServices = mcpDiscoveryService.getMcpServiceNames();
        this.allMcpServiceURLs = mcpDiscoveryService.getAllMcpServiceUrls();
        this.eventPublisher = eventPublisher;
        this.mcpClientFactory = mcpClientFactory;
        this.mcpServersWithHealth = new ArrayList<>();
        this.healthyMcpServiceURLs = new ArrayList<>();

        // Initialize protocol-aware services from new discovery method
        List<McpDiscoveryService.McpServiceConfiguration> serviceConfigs = mcpDiscoveryService.getMcpServicesWithProtocol();
        this.mcpServerServices.addAll(serviceConfigs.stream()
                .map(config -> new McpServerService(config.serviceName(), config.serverUrl(), config.protocol(), mcpClientFactory))
                .collect(Collectors.toList()));

        if (!allMcpServiceURLs.isEmpty()) {
            logger.info("Found MCP Services: {}", allMcpServiceURLs);
        }
        
        if (!mcpServerServices.isEmpty()) {
            logger.info("Found {} MCP services with protocol information", mcpServerServices.size());
            mcpServerServices.forEach(service -> 
                logger.debug("MCP Service: {} -> {} ({})", service.getName(), service.getServerUrl(), service.getProtocol().getDisplayName())
            );
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void publishConfigurationEvent() {
        testMcpServerHealth();

        logger.debug("Publishing ChatConfigurationEvent: chatModel={}, mcpServersWithHealth={}",
                chatModel, mcpServersWithHealth);
        eventPublisher.publishEvent(new ChatConfigurationEvent(this, chatModel, mcpServersWithHealth));
    }

    @Bean
    public List<String> mcpServiceURLs() {
        return healthyMcpServiceURLs; // Only return healthy MCP service URLs
    }

    @Bean
    public Map<String, String> serverNamesByUrl() {
        return serverNamesByUrl;
    }

    /**
     * Test the health of all configured MCP servers using the new protocol-aware architecture.
     * Falls back to legacy method if no protocol-aware services are found.
     */
    private void testMcpServerHealth() {
        mcpServersWithHealth.clear();
        healthyMcpServiceURLs.clear();
        serverNamesByUrl.clear();

        // Use new protocol-aware services if available
        if (!mcpServerServices.isEmpty()) {
            testProtocolAwareMcpServerHealth();
        }
        // Fallback to legacy method for backward compatibility
        else if (!allMcpServiceURLs.isEmpty()) {
            testLegacyMcpServerHealth();
        } else {
            logger.debug("No MCP services configured for health checking");
            return;
        }

        int healthyCount = healthyMcpServiceURLs.size();
        int totalCount = mcpServersWithHealth.size();

        logger.info("MCP Server health check completed. Healthy: {}, Unhealthy: {}",
                healthyCount, totalCount - healthyCount);

        if (healthyCount > 0) {
            logger.info("Healthy MCP servers that will be used for chat: {}", healthyMcpServiceURLs);
        }

        if (healthyCount < totalCount) {
            logger.warn("Some MCP servers are unhealthy and will not be used for chat operations");
        }
    }

    /**
     * Test the health of MCP servers using the new protocol-aware architecture.
     */
    private void testProtocolAwareMcpServerHealth() {
        logger.debug("Testing MCP server health using protocol-aware services");
        
        for (McpServerService serverService : mcpServerServices) {
            logger.debug("Testing health of MCP server: {} at {} ({})", 
                serverService.getName(), serverService.getServerUrl(), serverService.getProtocol().getDisplayName());

            McpServer mcpServer = serverService.getHealthyMcpServer();
            mcpServersWithHealth.add(mcpServer);

            // Store server name mapping for other services
            serverNamesByUrl.put(serverService.getServerUrl(), mcpServer.serverName());

            // Only add healthy servers to the list used by ChatService
            if (mcpServer.healthy()) {
                healthyMcpServiceURLs.add(serverService.getServerUrl());
            }
        }
    }

    /**
     * Legacy health testing method for backward compatibility.
     */
    private void testLegacyMcpServerHealth() {
        logger.debug("Testing MCP server health using legacy method");
        
        // If we have more URLs than service names (e.g., from GenaiLocator),
        // create synthetic service names
        List<String> serviceNames = ensureServiceNames(agentServices, allMcpServiceURLs);

        for (int i = 0; i < serviceNames.size() && i < allMcpServiceURLs.size(); i++) {
            String serviceName = serviceNames.get(i);
            String serviceUrl = allMcpServiceURLs.get(i);

            McpServer mcpServer = testMcpServerHealthAndGetTools(serviceName, serviceUrl);
            mcpServersWithHealth.add(mcpServer);

            // Only add healthy servers to the list used by ChatService
            if (mcpServer.healthy()) {
                healthyMcpServiceURLs.add(serviceUrl);
            }
        }
    }

    /**
     * Ensures we have service names for all URLs.
     * When using GenaiLocator, we might have URLs without corresponding CF service names.
     */
    private List<String> ensureServiceNames(List<String> cfServiceNames, List<String> allUrls) {
        List<String> result = new ArrayList<>(cfServiceNames);

        // If we have more URLs than service names, generate synthetic names
        while (result.size() < allUrls.size()) {
            String url = allUrls.get(result.size());
            String syntheticName = generateServiceNameFromUrl(url);
            result.add(syntheticName);
            logger.debug("Generated synthetic service name '{}' for URL '{}'", syntheticName, url);
        }

        return result;
    }

    /**
     * Generates a service name from a URL for MCP servers discovered via GenaiLocator.
     */
    private String generateServiceNameFromUrl(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String host = uri.getHost();
            int port = uri.getPort();

            if (host != null) {
                if (port != -1 && port != 80 && port != 443) {
                    return host + "-" + port;
                } else {
                    return host;
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to parse URL '{}' for service name generation: {}", url, e.getMessage());
        }

        // Fallback: use URL hash
        return "mcp-server-" + Math.abs(url.hashCode() % 10000);
    }

    /**
     * Test the health of a single MCP server by attempting to initialize it and get its tools.
     */
    private McpServer testMcpServerHealthAndGetTools(String serviceName, String serviceUrl) {
        logger.debug("Testing health of MCP server: {} at {}", serviceName, serviceUrl);

        List<McpServer.Tool> tools = new ArrayList<>();
        String serverName = serviceName; // Default to service name

        try {
            McpSyncClient client = mcpClientFactory.createHealthCheckClient(serviceUrl);

            // Attempt to initialize the client
            var initResult = client.initialize();

            // Get server name from initialization result
            if (initResult != null && initResult.serverInfo() != null && initResult.serverInfo().name() != null) {
                serverName = initResult.serverInfo().name();
                logger.debug("Retrieved server name '{}' for MCP server at {}", serverName, serviceUrl);
            } else {
                logger.debug("No server name available for MCP server at {}, using service name '{}'", serviceUrl, serviceName);
            }

            // Store the server name for use by other services
            serverNamesByUrl.put(serviceUrl, serverName);

            // If we get here, the server is healthy - now get the tools
            logger.debug("MCP server {} is healthy, fetching tools...", serviceName);

            try {
                var listToolsResult = client.listTools();
                if (listToolsResult != null && listToolsResult.tools() != null) {
                    tools = listToolsResult.tools().stream()
                            .map(tool -> new McpServer.Tool(tool.name(), tool.description()))
                            .toList();
                    logger.debug("Found {} tools for MCP server {}: {}",
                            tools.size(), serviceName,
                            tools.stream().map(McpServer.Tool::name).toList());
                }
            } catch (Exception e) {
                logger.warn("Failed to get tools for MCP server {} (server is healthy but tools unavailable): {}",
                        serviceName, e.getMessage());
            }

            // Clean up the test client
            client.closeGracefully();

            return new McpServer(serviceName, serverName, true, tools, ProtocolType.SSE);

        } catch (Exception e) {
            logger.warn("MCP server {} at {} is unhealthy: {}", serviceName, serviceUrl, e.getMessage());
            return new McpServer(serviceName, serverName, false, List.of(), ProtocolType.SSE);
        }
    }
}