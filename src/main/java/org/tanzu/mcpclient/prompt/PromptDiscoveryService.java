package org.tanzu.mcpclient.prompt;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.tanzu.mcpclient.chat.ChatConfigurationEvent;
import org.tanzu.mcpclient.mcp.McpClientFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service responsible for discovering and managing MCP prompts from connected servers.
 * This service automatically discovers prompts at startup and maintains them in memory
 * for efficient access during runtime.
 *
 * <p>The service listens for ChatConfigurationEvent to ensure MCP servers are available
 * before attempting prompt discovery. It handles multi-server environments by namespacing
 * prompts with their server IDs to prevent conflicts.</p>
 */
@Service
public class PromptDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(PromptDiscoveryService.class);

    private final List<String> mcpServiceURLs;
    private final McpClientFactory mcpClientFactory;
    private final Map<String, String> serverNamesByUrl;
    private final Map<String, List<McpPrompt>> promptsByServer = new ConcurrentHashMap<>();
    private final Map<String, McpPrompt> promptsById = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;

    public PromptDiscoveryService(List<String> mcpServiceURLs,
                                  McpClientFactory mcpClientFactory,
                                  Map<String, String> serverNamesByUrl,
                                  ApplicationEventPublisher eventPublisher) { // Add this parameter
        this.mcpServiceURLs = mcpServiceURLs;
        this.mcpClientFactory = mcpClientFactory;
        this.serverNamesByUrl = serverNamesByUrl;
        this.eventPublisher = eventPublisher; // Add this
    }

    /**
     * Listens for ChatConfigurationEvent and triggers prompt discovery once MCP servers are ready.
     */
    @EventListener
    public void onChatConfigurationReady(ChatConfigurationEvent event) {
        if (!mcpServiceURLs.isEmpty()) {
            logger.info("Starting prompt discovery for {} MCP servers", mcpServiceURLs.size());
            discoverPrompts();
        } else {
            logger.debug("No MCP service URLs configured, skipping prompt discovery");
        }
    }

    /**
     * Discovers prompts from all configured MCP servers and publishes configuration event.
     */
    private void discoverPrompts() {
        promptsByServer.clear();
        promptsById.clear();

        int serversWithPrompts = 0;
        int serversWithoutPrompts = 0;

        for (String mcpUrl : mcpServiceURLs) {
            try {
                boolean hasPrompts = discoverPromptsFromServer(mcpUrl);
                if (hasPrompts) {
                    serversWithPrompts++;
                } else {
                    serversWithoutPrompts++;
                }
            } catch (Exception e) {
                logger.warn("Failed to discover prompts from server {}: {}", mcpUrl, e.getMessage());
                serversWithoutPrompts++;
            }
        }

        logger.info("Prompt discovery completed. Total prompts: {}, Servers with prompts: {}, Servers without prompts: {}",
                promptsById.size(), serversWithPrompts, serversWithoutPrompts);

        // Publish event after discovery is complete
        publishPromptConfigurationEvent(serversWithPrompts);
    }

    /**
     * Publishes PromptConfigurationEvent with current prompt state.
     */
    private void publishPromptConfigurationEvent(int serversWithPrompts) {
        logger.debug("Publishing PromptConfigurationEvent: totalPrompts={}, serversWithPrompts={}, available={}",
                promptsById.size(), serversWithPrompts, hasPrompts());

        eventPublisher.publishEvent(new PromptConfigurationEvent(
                this,
                promptsById.size(),
                serversWithPrompts,
                hasPrompts(),
                getPromptsByServer()
        ));
    }

    /**
     * Discovers prompts from a single MCP server.
     *
     * @param mcpUrl The MCP server URL
     * @return true if the server has prompts, false if it doesn't support prompts
     */
    private boolean discoverPromptsFromServer(String mcpUrl) {
        String serverId = generateServerId(mcpUrl);
        String initialServerName = getServerDisplayName(mcpUrl, serverId);

        try (var mcpClient = createMcpClient(mcpUrl)) {
            var initResult = mcpClient.initialize();

            // Get the final server name to use (effectively final for lambda expressions)
            final String finalServerName;
            if (initResult != null && initResult.serverInfo() != null && initResult.serverInfo().name() != null) {
                finalServerName = initResult.serverInfo().name();
                serverNamesByUrl.put(mcpUrl, finalServerName);
                logger.debug("Updated server name '{}' for MCP server at {} during prompt discovery", finalServerName, mcpUrl);
            } else {
                finalServerName = initialServerName;
            }

            var listPromptsResult = mcpClient.listPrompts();
            if (listPromptsResult != null && listPromptsResult.prompts() != null && !listPromptsResult.prompts().isEmpty()) {
                List<McpPrompt> serverPrompts = listPromptsResult.prompts().stream()
                        .map(prompt -> convertToMcpPrompt(serverId, finalServerName, prompt))
                        .collect(Collectors.toList());

                promptsByServer.put(serverId, serverPrompts);

                // Create unique IDs for global lookup
                serverPrompts.forEach(prompt -> {
                    String promptId = serverId + ":" + prompt.name();
                    promptsById.put(promptId, prompt);
                });

                logger.debug("Discovered {} prompts from server '{}' ({})",
                        serverPrompts.size(), finalServerName, mcpUrl);
                return true;
            } else {
                logger.debug("Server '{}' ({}) returned no prompts", finalServerName, mcpUrl);
                return false;
            }
        } catch (McpError e) {
            if (e.getMessage() != null && e.getMessage().contains("Method not found: prompts/list")) {
                logger.debug("Server '{}' ({}) does not support prompts (tools-only server)", initialServerName, mcpUrl);
                return false;
            } else {
                // Re-throw other MCP errors
                throw e;
            }
        } catch (Exception e) {
            logger.error("Error discovering prompts from server '{}' ({}): {}",
                    initialServerName, mcpUrl, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Gets the display name for a server, preferring the stored server name.
     */
    private String getServerDisplayName(String mcpUrl, String serverId) {
        String serverName = serverNamesByUrl.get(mcpUrl);
        return serverName != null && !serverName.trim().isEmpty() ? serverName : serverId;
    }

    /**
     * Creates a temporary MCP client for prompt discovery.
     */
    private McpSyncClient createMcpClient(String mcpUrl) {
        return mcpClientFactory.createMcpSyncClient(mcpUrl);
    }

    /**
     * Generates a server ID from the MCP URL for namespacing.
     */
    private String generateServerId(String mcpUrl) {
        try {
            var uri = java.net.URI.create(mcpUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            return port != -1 ? host + ":" + port : host;
        } catch (Exception e) {
            // Fallback to URL hash if parsing fails
            return "server-" + Math.abs(mcpUrl.hashCode());
        }
    }

    /**
     * Converts an MCP schema prompt to our internal McpPrompt representation.
     */
    private McpPrompt convertToMcpPrompt(String serverId, String serverName, McpSchema.Prompt prompt) {
        List<PromptArgument> arguments = Optional.ofNullable(prompt.arguments())
                .orElse(Collections.emptyList())
                .stream()
                .map(this::convertToPromptArgument)
                .collect(Collectors.toList());

        return new McpPrompt(
                serverId,
                serverName,
                prompt.name(),
                prompt.description(),
                arguments
        );
    }

    /**
     * Converts an MCP schema prompt argument to our internal PromptArgument representation.
     */
    private PromptArgument convertToPromptArgument(McpSchema.PromptArgument arg) {
        return new PromptArgument(
                arg.name(),
                arg.description(),
                Optional.ofNullable(arg.required()).orElse(false),
                null, // Default value not provided in MCP schema
                null  // JSON schema not provided in MCP prompt arguments
        );
    }

    /**
     * Returns prompts grouped by server ID.
     */
    public Map<String, List<McpPrompt>> getPromptsByServer() {
        return new HashMap<>(promptsByServer);
    }

    /**
     * Finds a prompt by its unique ID (serverId:promptName).
     */
    public Optional<McpPrompt> findPromptById(String promptId) {
        return Optional.ofNullable(promptsById.get(promptId));
    }

    /**
     * Returns the count of discovered prompts.
     */
    public int getPromptCount() {
        return promptsById.size();
    }

    /**
     * Returns the count of servers that have prompts.
     */
    public int getServerCount() {
        return promptsByServer.size();
    }

    /**
     * Checks if any prompts are available.
     */
    public boolean hasPrompts() {
        return !promptsById.isEmpty();
    }
}