package org.tanzu.mcpclient.prompt;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.tanzu.mcpclient.mcp.McpClientFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for resolving MCP prompts with user-provided arguments.
 * This service handles argument validation, prompt resolution calls to MCP servers,
 * and conversion of responses to internal representations.
 *
 * <p>The service maintains connections to MCP servers as needed and handles
 * prompt resolution requests with proper error handling and validation.</p>
 */
@Service
public class PromptResolutionService {

    private static final Logger logger = LoggerFactory.getLogger(PromptResolutionService.class);

    private final PromptDiscoveryService promptDiscoveryService;
    private final List<String> mcpServiceURLs;
    private final McpClientFactory mcpClientFactory;

    public PromptResolutionService(PromptDiscoveryService promptDiscoveryService,
                                   List<String> mcpServiceURLs,
                                   McpClientFactory mcpClientFactory) {
        this.promptDiscoveryService = promptDiscoveryService;
        this.mcpServiceURLs = mcpServiceURLs;
        this.mcpClientFactory = mcpClientFactory;
    }

    /**
     * Resolves a prompt with the provided arguments.
     *
     * @throws PromptResolutionException if the prompt cannot be resolved
     */
    public ResolvedPrompt resolvePrompt(PromptResolutionRequest request) {
        logger.debug("Resolving prompt: {}", request.promptId());

        // Find the prompt definition
        Optional<McpPrompt> promptOpt = promptDiscoveryService.findPromptById(request.promptId());
        if (promptOpt.isEmpty()) {
            throw new PromptResolutionException("Prompt not found: " + request.promptId());
        }

        McpPrompt prompt = promptOpt.get();

        // Validate arguments
        validateArguments(prompt, request.arguments());

        // Find the server URL for this prompt
        String serverUrl = findServerUrl(prompt.serverId());
        if (serverUrl == null) {
            throw new PromptResolutionException("Server not found for prompt: " + prompt.serverId());
        }

        // Resolve the prompt with the MCP server
        return resolvePromptWithServer(serverUrl, prompt, request.arguments());
    }

    /**
     * Validates that all required arguments are provided and that values are reasonable.
     */
    private void validateArguments(McpPrompt prompt, Map<String, Object> providedArgs) {
        if (prompt.arguments() == null) {
            return; // No arguments required
        }

        Set<String> providedArgNames = providedArgs != null ? providedArgs.keySet() : Collections.emptySet();

        // Check for missing required arguments
        List<String> missingRequired = prompt.arguments().stream()
                .filter(PromptArgument::required)
                .map(PromptArgument::name)
                .filter(name -> !providedArgNames.contains(name))
                .toList();

        if (!missingRequired.isEmpty()) {
            throw new PromptResolutionException("Missing required arguments: " + missingRequired);
        }

        // Check for unknown arguments
        Set<String> validArgNames = prompt.arguments().stream()
                .map(PromptArgument::name)
                .collect(Collectors.toSet());

        List<String> unknownArgs = providedArgNames.stream()
                .filter(name -> !validArgNames.contains(name))
                .collect(Collectors.toList());

        if (!unknownArgs.isEmpty()) {
            logger.warn("Unknown arguments provided for prompt {}: {}", prompt.name(), unknownArgs);
        }

        // Validate argument values (basic validation)
        if (providedArgs != null) {
            providedArgs.forEach((name, value) -> {
                if (value == null) {
                    logger.warn("Null value provided for argument: {}", name);
                } else if (value instanceof String && ((String) value).trim().isEmpty()) {
                    logger.warn("Empty string provided for argument: {}", name);
                }
            });
        }
    }

    /**
     * Finds the server URL corresponding to a server ID.
     */
    private String findServerUrl(String serverId) {
        return mcpServiceURLs.stream()
                .filter(url -> generateServerId(url).equals(serverId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Generates a server ID from URL (same logic as PromptDiscoveryService).
     */
    private String generateServerId(String mcpUrl) {
        try {
            var uri = java.net.URI.create(mcpUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            return port != -1 ? host + ":" + port : host;
        } catch (Exception e) {
            return "server-" + Math.abs(mcpUrl.hashCode());
        }
    }

    /**
     * Resolves the prompt by making a call to the MCP server.
     */
    private ResolvedPrompt resolvePromptWithServer(String serverUrl, McpPrompt prompt, Map<String, Object> arguments) {
        try (McpSyncClient mcpClient = createMcpClient(serverUrl)) {
            mcpClient.initialize();

            // Prepare the get prompt request
            Map<String, Object> args = arguments != null ? arguments : Collections.emptyMap();

            // Add default values for missing optional arguments
            Map<String, Object> finalArgs = new HashMap<>(args);
            if (prompt.arguments() != null) {
                prompt.arguments().stream()
                        .filter(arg -> !arg.required() && arg.hasDefaultValue() && !finalArgs.containsKey(arg.name()))
                        .forEach(arg -> finalArgs.put(arg.name(), arg.defaultValue()));
            }

            McpSchema.GetPromptRequest getPromptRequest = new McpSchema.GetPromptRequest(
                    prompt.name(),
                    finalArgs
            );

            // Call the MCP server to resolve the prompt
            McpSchema.GetPromptResult result = mcpClient.getPrompt(getPromptRequest);

            return convertToResolvedPrompt(result);

        } catch (Exception e) {
            logger.error("Failed to resolve prompt {} on server {}: {}",
                    prompt.name(), serverUrl, e.getMessage(), e);
            throw new PromptResolutionException("Failed to resolve prompt: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an MCP client for communication with a server.
     */
    private McpSyncClient createMcpClient(String serverUrl) {
        return mcpClientFactory.createMcpSyncClient(serverUrl);
    }

    /**
     * Converts MCP GetPromptResult to our internal ResolvedPrompt representation.
     */
    private ResolvedPrompt convertToResolvedPrompt(McpSchema.GetPromptResult result) {
        String content = extractContentFromResult(result);
        List<PromptMessage> messages = convertMessages(result.messages());

        Map<String, Object> metadata = new HashMap<>();
        if (result.description() != null) {
            metadata.put("description", result.description());
        }

        return new ResolvedPrompt(content, messages, metadata);
    }

    /**
     * Extracts content from the MCP result.
     * If messages are present, concatenates their content.
     * Otherwise, returns description or empty string.
     */
    private String extractContentFromResult(McpSchema.GetPromptResult result) {
        if (result.messages() != null && !result.messages().isEmpty()) {
            return result.messages().stream()
                    .map(this::extractContentFromMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n\n"));
        }

        return result.description() != null ? result.description() : "";
    }

    /**
     * Extracts content from an MCP prompt message.
     */
    private String extractContentFromMessage(McpSchema.PromptMessage message) {
        McpSchema.Content content = message.content();

        return switch (content) {
            case McpSchema.TextContent textContent -> textContent.text();
            case McpSchema.ImageContent imageContent -> "[Image: " + imageContent.mimeType() + "]";
            case McpSchema.EmbeddedResource embeddedResource -> "[Resource: " + embeddedResource.resource().uri() + "]";
            default -> "[" + content.getClass().getSimpleName() + " content]";
        };
    }

    /**
     * Converts MCP prompt messages to our internal representation.
     */
    private List<PromptMessage> convertMessages(List<McpSchema.PromptMessage> mcpMessages) {
        if (mcpMessages == null) {
            return null;
        }

        return mcpMessages.stream()
                .map(this::convertMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Converts a single MCP prompt message to our internal representation.
     */
    private PromptMessage convertMessage(McpSchema.PromptMessage mcpMessage) {
        if (mcpMessage == null) {
            return null;
        }

        String role = mcpMessage.role() != null ? mcpMessage.role().toString() : PromptMessage.Roles.USER;
        String content = extractContentFromMessage(mcpMessage);

        return new PromptMessage(role, content);
    }

    /**
     * Exception thrown when prompt resolution fails.
     */
    public static class PromptResolutionException extends RuntimeException {
        public PromptResolutionException(String message) {
            super(message);
        }

        public PromptResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}