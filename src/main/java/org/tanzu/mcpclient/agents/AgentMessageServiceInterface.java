package org.tanzu.mcpclient.agents;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interface for agent messaging services.
 * Allows for different implementations (RabbitMQ, in-memory, etc.)
 */
public interface AgentMessageServiceInterface {

    /**
     * Sends a request to an agent with streaming response handlers.
     * Each response is delivered immediately via the provided handlers.
     *
     * @param agentType The type of agent (e.g., "reviewer")
     * @param prompt The prompt to send to the agent
     * @param userId The user ID making the request
     * @param onResponse Called for each response as it arrives
     * @param onError Called if an error occurs
     * @param onComplete Called when the response sequence is complete
     * @return CompletableFuture that completes when the entire sequence is done
     */
    CompletableFuture<Void> sendAgentRequest(String agentType, String prompt, String userId,
                                             Consumer<AgentResponse> onResponse,
                                             Consumer<Throwable> onError,
                                             Runnable onComplete);

    /**
     * Convenience method for backward compatibility.
     * Returns a CompletableFuture with the first response only.
     */
    CompletableFuture<AgentResponse> sendAgentRequest(String agentType, String prompt, String userId);

    /**
     * Gets the current connection status for monitoring.
     *
     * @return Status information about the messaging system
     */
    Map<String, Object> getConnectionStatus();

    /**
     * Clears any active handlers (useful for cleanup).
     */
    void clearActiveHandlers();

    /**
     * Gets the number of active response handlers.
     *
     * @return Number of active handlers
     */
    int getActiveHandlerCount();
}