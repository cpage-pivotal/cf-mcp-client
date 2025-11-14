package org.tanzu.mcpclient.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * A ToolCallbackProvider wrapper that handles MCP server session recovery.
 *
 * When an MCP server restarts, the session ID becomes invalid and subsequent
 * tool calls fail with "Session not found" errors. This wrapper detects such
 * errors and automatically creates a new MCP client connection to recover.
 *
 * Key features:
 * - Detects session errors (404 with "Session not found" message)
 * - Automatically reconnects to MCP server with a fresh session
 * - Retries the failed operation once with the new session
 * - Thread-safe client recreation using AtomicReference
 */
public class SessionRecoveringToolCallbackProvider implements ToolCallbackProvider {

    private static final Logger logger = LoggerFactory.getLogger(SessionRecoveringToolCallbackProvider.class);

    private final String serverName;
    private final Supplier<McpSyncClient> clientFactory;
    private final AtomicReference<SyncMcpToolCallbackProvider> delegateRef;

    /**
     * Creates a new session-recovering tool callback provider.
     *
     * @param serverName The name of the MCP server (for logging)
     * @param clientFactory A supplier that creates and initializes a new MCP client
     */
    public SessionRecoveringToolCallbackProvider(String serverName, Supplier<McpSyncClient> clientFactory) {
        this.serverName = serverName;
        this.clientFactory = clientFactory;
        this.delegateRef = new AtomicReference<>(createDelegate());
    }

    /**
     * Creates a new delegate provider by creating and initializing a fresh MCP client.
     */
    private SyncMcpToolCallbackProvider createDelegate() {
        try {
            logger.debug("Creating new MCP client for server: {}", serverName);
            McpSyncClient client = clientFactory.get();
            client.initialize();
            return new SyncMcpToolCallbackProvider(client);
        } catch (Exception e) {
            logger.error("Failed to create MCP client for {}: {}", serverName, e.getMessage(), e);
            throw new RuntimeException("Failed to create MCP client for " + serverName, e);
        }
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        SyncMcpToolCallbackProvider delegate = delegateRef.get();
        ToolCallback[] originalCallbacks = delegate.getToolCallbacks();

        // Wrap each callback to handle session errors
        return Arrays.stream(originalCallbacks)
                .map(this::wrapToolCallback)
                .toArray(ToolCallback[]::new);
    }

    /**
     * Wraps a ToolCallback to add session recovery capabilities.
     */
    private ToolCallback wrapToolCallback(ToolCallback originalCallback) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return originalCallback.getToolDefinition();
            }

            @Override
            public String call(String functionArguments) {
                try {
                    return originalCallback.call(functionArguments);
                } catch (Exception e) {
                    // Check if this is a session error
                    if (isSessionError(e)) {
                        logger.warn("Session error detected for MCP server {}: {}. Attempting recovery...",
                                serverName, e.getMessage());

                        try {
                            // Create a new client and update the delegate
                            SyncMcpToolCallbackProvider newDelegate = createDelegate();
                            delegateRef.set(newDelegate);

                            logger.info("Successfully reconnected to MCP server: {}. Retrying tool invocation...",
                                    serverName);

                            // Find the corresponding callback in the new delegate and retry
                            ToolDefinition toolDef = originalCallback.getToolDefinition();
                            ToolCallback newCallback = Arrays.stream(newDelegate.getToolCallbacks())
                                    .filter(cb -> cb.getToolDefinition().equals(toolDef))
                                    .findFirst()
                                    .orElseThrow(() -> new RuntimeException("Tool not found after reconnection"));

                            return newCallback.call(functionArguments);
                        } catch (Exception reconnectError) {
                            logger.error("Failed to recover from session error for MCP server {}: {}",
                                    serverName, reconnectError.getMessage(), reconnectError);
                            throw new RuntimeException("Session recovery failed for " + serverName, reconnectError);
                        }
                    }

                    // Not a session error, rethrow
                    throw e;
                }
            }
        };
    }

    /**
     * Determines if an exception is caused by an invalid MCP session.
     *
     * Session errors can manifest in several ways depending on the transport:
     * - Streamable HTTP: "Session not found" with HTTP 404
     * - SSE: "Invalid session ID" with HTTP 400 and JSON-RPC error -32602
     * - Generic: "MCP session with server terminated"
     * - Exception type: McpTransportSessionNotFoundException
     *
     * @param e The exception to check
     * @return true if this appears to be a session error
     */
    private boolean isSessionError(Exception e) {
        // Look for session error patterns in the full exception chain
        Throwable current = e;
        while (current != null) {
            // Check exception type
            String className = current.getClass().getName();
            if (className.contains("McpTransportSessionNotFoundException") ||
                className.contains("SessionNotFoundException")) {
                logger.debug("Detected session error by exception type: {}", className);
                return true;
            }

            // Check exception message
            String currentMessage = current.getMessage();
            if (currentMessage != null) {
                // Session-related error messages (both Streamable HTTP and SSE)
                if (currentMessage.contains("Session not found") ||
                    currentMessage.contains("session not found") ||
                    currentMessage.contains("Invalid session ID") ||
                    currentMessage.contains("invalid session") ||
                    currentMessage.contains("MCP session with server terminated") ||
                    currentMessage.contains("session with server terminated")) {
                    logger.debug("Detected session error by message: {}", currentMessage);
                    return true;
                }

                // HTTP status codes indicating session issues
                // 404 for Streamable HTTP, 400 for SSE
                if (currentMessage.contains("404") || currentMessage.contains("400")) {
                    // Be more specific for 400 - only treat as session error if it mentions session
                    if (currentMessage.contains("400")) {
                        if (currentMessage.toLowerCase().contains("session")) {
                            logger.debug("Detected session error by 400 status with session keyword: {}", currentMessage);
                            return true;
                        }
                    } else {
                        // 404 is typically session-related in MCP context
                        logger.debug("Detected session error by 404 status in message: {}", currentMessage);
                        return true;
                    }
                }

                // JSON-RPC error code -32602 (Invalid params) often indicates invalid session ID
                if (currentMessage.contains("-32602") && currentMessage.toLowerCase().contains("session")) {
                    logger.debug("Detected session error by JSON-RPC error code -32602 with session keyword: {}", currentMessage);
                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }
}
