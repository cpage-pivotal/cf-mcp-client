package org.tanzu.mcpclient.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
    public List<ToolCallback> getToolCallbacks() {
        SyncMcpToolCallbackProvider delegate = delegateRef.get();
        List<ToolCallback> originalCallbacks = delegate.getToolCallbacks();

        // Wrap each callback to handle session errors
        return originalCallbacks.stream()
                .map(this::wrapToolCallback)
                .collect(Collectors.toList());
    }

    /**
     * Wraps a ToolCallback to add session recovery capabilities.
     */
    private ToolCallback wrapToolCallback(ToolCallback originalCallback) {
        return new ToolCallback() {
            @Override
            public String getToolDefinition() {
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
                            String toolDef = originalCallback.getToolDefinition();
                            ToolCallback newCallback = newDelegate.getToolCallbacks().stream()
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
     * Session errors typically contain:
     * - "Session not found" in the error message
     * - HTTP 404 status code
     * - "Sending message failed with a non-OK HTTP code: 404"
     *
     * @param e The exception to check
     * @return true if this appears to be a session error
     */
    private boolean isSessionError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }

        // Check for session-related error messages
        boolean hasSessionMessage = message.contains("Session not found") ||
                                   message.contains("session not found");

        // Check for 404 status
        boolean has404Status = message.contains("404");

        // Look for these patterns in the full exception chain
        Throwable current = e;
        while (current != null) {
            String currentMessage = current.getMessage();
            if (currentMessage != null) {
                if (currentMessage.contains("Session not found") ||
                    currentMessage.contains("session not found")) {
                    hasSessionMessage = true;
                }
                if (currentMessage.contains("404")) {
                    has404Status = true;
                }
            }
            current = current.getCause();
        }

        return hasSessionMessage || has404Status;
    }
}
