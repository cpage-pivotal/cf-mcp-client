package org.tanzu.mcpclient.chat;

import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
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

import java.time.Duration;
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

    // Map to store server names by URL for use by other services
    private final Map<String, String> serverNamesByUrl = new ConcurrentHashMap<>();
    // Protocol-aware MCP server services
    private final List<McpServerService> mcpServerServices = new ArrayList<>();

    public ChatConfiguration(ModelDiscoveryService modelDiscoveryService, McpDiscoveryService mcpDiscoveryService,
                             ApplicationEventPublisher eventPublisher, McpClientFactory mcpClientFactory) {
        this.chatModel = modelDiscoveryService.getChatModelName();
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
                .toList());

        logger.info("ChatConfiguration initialized with {} MCP server services", mcpServerServices.size());
        mcpServerServices.forEach(service ->
                logger.debug("Configured MCP service: {} at {} using {}",
                        service.getName(), service.getServerUrl(), service.getProtocol().displayName()));
    }

    /**
     * UPDATED: Creates ChatService bean using McpServerService instances with protocol information
     * instead of raw URL strings for reliable protocol detection
     */
    @Bean
    public ChatService chatService(ChatClient.Builder chatClientBuilder,
                                   BaseChatMemoryAdvisor memoryAdvisor,
                                   VectorStore vectorStore,
                                   ModelDiscoveryService modelDiscoveryService) {

        logger.info("Creating ChatService with {} protocol-aware MCP server services", mcpServerServices.size());

        return new ChatService(
                chatClientBuilder,
                memoryAdvisor,
                mcpServerServices, // Pass McpServerService instances instead of raw URLs
                vectorStore,
                mcpClientFactory,
                modelDiscoveryService
        );
    }

    @EventListener
    public void handleApplicationReady(ApplicationReadyEvent event) {
        logger.info("Application ready, testing MCP server health...");
        testMcpServerHealth();

        // Publish metrics update event
        eventPublisher.publishEvent(new ChatConfigurationEvent(this, chatModel, mcpServersWithHealth));

        logger.info("Chat configuration complete. {} healthy MCP servers available.", healthyMcpServiceURLs.size());
        if (!healthyMcpServiceURLs.isEmpty()) {
            logger.info("Healthy MCP servers: {}", healthyMcpServiceURLs);
        }
    }

    private void testMcpServerHealth() {
        logger.debug("Testing MCP server health using protocol-aware and legacy methods");

        // Test protocol-aware services first
        testProtocolAwareMcpServerHealth();

        // Test legacy services for backward compatibility
        testLegacyMcpServerHealth();
    }

    /**
     * Test health of protocol-aware MCP server services
     */
    private void testProtocolAwareMcpServerHealth() {
        logger.debug("Testing MCP server health using protocol-aware services");

        for (McpServerService serverService : mcpServerServices) {
            logger.debug("Testing health of MCP server: {} at {} ({})",
                    serverService.getName(), serverService.getServerUrl(), serverService.getProtocol().displayName());

            McpServer mcpServer = serverService.getHealthyMcpServer();
            mcpServersWithHealth.add(mcpServer);

            // Store server name mapping and track healthy servers
            serverNamesByUrl.put(serverService.getServerUrl(), mcpServer.serverName());
            if (mcpServer.healthy()) {
                healthyMcpServiceURLs.add(serverService.getServerUrl());
            }
        }
    }

    /**
     * Test health of legacy MCP services for backward compatibility
     */
    private void testLegacyMcpServerHealth() {
        logger.debug("Testing MCP server health using legacy URL-based method");

        // Get URLs that are not already covered by protocol-aware services
        List<String> protocolAwareUrls = mcpServerServices.stream()
                .map(McpServerService::getServerUrl)
                .toList();

        List<String> legacyUrls = allMcpServiceURLs.stream()
                .filter(url -> !protocolAwareUrls.contains(url))
                .toList();

        for (String mcpServiceUrl : legacyUrls) {
            logger.debug("Testing health of legacy MCP server at: {}", mcpServiceUrl);

            try (McpSyncClient mcpSyncClient = mcpClientFactory.createSseClient(mcpServiceUrl,
                    Duration.ofSeconds(30), Duration.ofMinutes(5))) {

                mcpSyncClient.initialize();

                // Create McpServer record with protocol information
                String serverName = serverNamesByUrl.getOrDefault(mcpServiceUrl,
                        agentServices.stream()
                                .filter(mcpServiceUrl::contains)
                                .findFirst()
                                .orElse("Unknown"));

                // Convert McpSchema.Tool to McpServer.Tool properly
                List<McpServer.Tool> convertedTools = mcpSyncClient.listTools().tools().stream()
                        .map(tool -> new McpServer.Tool(tool.name(), tool.description()))
                        .collect(Collectors.toList());

                // Use ProtocolType record instance instead of string
                McpServer mcpServer = new McpServer(serverName, serverName, true,
                        convertedTools, new ProtocolType.SSE()); // Use SSE record instance for legacy

                mcpServersWithHealth.add(mcpServer);
                serverNamesByUrl.put(mcpServiceUrl, serverName);
                healthyMcpServiceURLs.add(mcpServiceUrl);

                logger.debug("Legacy MCP server {} is healthy", mcpServiceUrl);

            } catch (Exception e) {
                logger.warn("Legacy MCP server {} is unhealthy: {}", mcpServiceUrl, e.getMessage());

                String serverName = serverNamesByUrl.getOrDefault(mcpServiceUrl, "Unknown");
                McpServer mcpServer = new McpServer(serverName, serverName, false,
                        List.of(), new ProtocolType.SSE()); // Use SSE record instance for legacy

                mcpServersWithHealth.add(mcpServer);
            }
        }
    }

    // Getter methods for other services that need access to configuration

    public List<String> getHealthyMcpServiceURLs() {
        return List.copyOf(healthyMcpServiceURLs);
    }

    public List<McpServerService> getMcpServerServices() {
        return List.copyOf(mcpServerServices);
    }

    public Map<String, String> getServerNamesByUrl() {
        return Map.copyOf(serverNamesByUrl);
    }

    public String getChatModel() {
        return chatModel;
    }

    public List<String> getAgentServices() {
        return List.copyOf(agentServices);
    }
}