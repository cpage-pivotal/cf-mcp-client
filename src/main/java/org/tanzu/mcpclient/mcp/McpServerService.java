package org.tanzu.mcpclient.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tanzu.mcpclient.metrics.McpServer;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing individual MCP server connections with protocol support.
 * Handles client creation, health checks, and tool discovery for a specific MCP server.
 */
public class McpServerService {
    private static final Logger logger = LoggerFactory.getLogger(McpServerService.class);

    private final String name;
    private final String serverUrl;
    private final ProtocolType protocol;
    private final McpClientFactory clientFactory;

    public McpServerService(String name, String serverUrl, ProtocolType protocol, McpClientFactory clientFactory) {
        this.name = name;
        this.serverUrl = serverUrl;
        this.protocol = protocol;
        this.clientFactory = clientFactory;
    }

    /**
     * Creates a new MCP synchronous client for this server using the appropriate protocol with fallback.
     */
    public McpSyncClient createMcpSyncClient() {
        return switch (protocol) {
            case ProtocolType.StreamableHttp streamableHttp ->
                    clientFactory.createStreamableClientWithFallback(serverUrl, Duration.ofSeconds(30), Duration.ofMinutes(5));
            case ProtocolType.SSE sse ->
                    clientFactory.createSseClientWithFallback(serverUrl, Duration.ofSeconds(30), Duration.ofMinutes(5));
            case ProtocolType.Legacy legacy ->
                    clientFactory.createSseClientWithFallback(serverUrl, Duration.ofSeconds(30), Duration.ofMinutes(5));
        };
    }

    /**
     * Creates a health check client for this server using the appropriate protocol with fallback.
     */
    public McpSyncClient createHealthCheckClient() {
        try {
            return switch (protocol) {
                case ProtocolType.StreamableHttp streamableHttp ->
                        clientFactory.createStreamableClientWithFallback(serverUrl, Duration.ofSeconds(10), Duration.ofSeconds(10));
                case ProtocolType.SSE sse ->
                        clientFactory.createSseClientWithFallback(serverUrl, Duration.ofSeconds(10), Duration.ofSeconds(10));
                case ProtocolType.Legacy legacy ->
                        clientFactory.createSseClientWithFallback(serverUrl, Duration.ofSeconds(10), Duration.ofSeconds(10));
            };
        } catch (McpConnectionException e) {
            logger.debug("Health check connection failed for {}: {}", name, e.getMessage());
            return null;
        }
    }

    /**
     * Gets the health status and available tools for this MCP server.
     * Now uses fallback connection strategy.
     */
    public McpServer getHealthyMcpServer() {
        McpSyncClient client = createHealthCheckClient();

        if (client == null) {
            // Connection failed entirely
            logger.warn("MCP server {} is not reachable (tried with and without suffix)", name);
            return new McpServer(name, name, false, Collections.emptyList(), protocol);
        }

        try (client) {
            // Initialize connection
            McpSchema.InitializeResult initResult = client.initialize();
            logger.debug("Initialized MCP server {}: protocol version {}", name,
                    initResult.protocolVersion());

            // Get server name from initialization result if available
            String serverName = initResult.serverInfo() != null
                    ? initResult.serverInfo().name()
                    : name;

            // Get available tools
            McpSchema.ListToolsResult toolsResult = client.listTools();

            // Convert McpSchema.Tool to McpServer.Tool
            List<McpServer.Tool> tools = toolsResult.tools().stream()
                    .map(tool -> new McpServer.Tool(tool.name(), tool.description()))
                    .collect(Collectors.toList());

            logger.info("MCP server {} is healthy with {} tools ({})",
                    serverName, tools.size(), protocol.displayName());

            return new McpServer(name, serverName, true, tools, protocol);

        } catch (Exception e) {
            logger.warn("Health check failed for MCP server {} ({}): {}",
                    name, protocol.displayName(), e.getMessage());
            return new McpServer(name, name, false, Collections.emptyList(), protocol);
        }
    }

    // Getter methods
    public String getName() {
        return name;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public ProtocolType getProtocol() {
        return protocol;
    }
}