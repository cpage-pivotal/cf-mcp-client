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
     * Creates a new MCP synchronous client for this server using the appropriate protocol.
     */
    public McpSyncClient createMcpSyncClient() {
        return switch (protocol) {
            case SSE -> clientFactory.createSseClient(serverUrl, Duration.ofSeconds(30), Duration.ofMinutes(5));
            case STREAMABLE_HTTP -> clientFactory.createStreamableClient(serverUrl, Duration.ofSeconds(30), Duration.ofMinutes(5));
        };
    }

    /**
     * Creates a health check client for this server using the appropriate protocol.
     */
    public McpSyncClient createHealthCheckClient() {
        return clientFactory.createHealthCheckClient(serverUrl, protocol);
    }

    /**
     * Gets the health status and available tools for this MCP server.
     * Returns an McpServer instance with health information.
     */
    public McpServer getHealthyMcpServer() {
        try (McpSyncClient client = createHealthCheckClient()) {
            // Initialize connection
            McpSchema.InitializeResult initResult = client.initialize();
            logger.debug("Initialized MCP server {}: protocol version {}", name, 
                initResult.protocolVersion());

            // Get server name from initialization result if available
            String serverName = initResult.serverInfo() != null 
                ? initResult.serverInfo().name() 
                : name;

            // List available tools
            McpSchema.ListToolsResult toolsResult = client.listTools();
            List<McpServer.Tool> tools = toolsResult.tools().stream()
                    .map(this::convertToTool)
                    .collect(Collectors.toList());

            logger.debug("MCP server {} health check successful: {} tools available", name, tools.size());
            return new McpServer(name, serverName, true, tools, protocol);

        } catch (Exception e) {
            logger.warn("Health check failed for MCP server {} ({}): {}", name, protocol.getDisplayName(), e.getMessage());
            return new McpServer(name, name, false, Collections.emptyList(), protocol);
        }
    }

    /**
     * Converts an MCP tool schema to our internal Tool representation.
     */
    private McpServer.Tool convertToTool(McpSchema.Tool mcpTool) {
        return new McpServer.Tool(
            mcpTool.name(),
            mcpTool.description() != null ? mcpTool.description() : "No description available"
        );
    }

    // Getters
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