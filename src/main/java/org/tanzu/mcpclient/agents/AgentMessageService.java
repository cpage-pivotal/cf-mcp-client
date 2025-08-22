package org.tanzu.mcpclient.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Service for handling agent message queue operations.
 * Manages correlation IDs and response routing as specified in AGENTS.md.
 *
 * Updated to stream individual responses immediately to the UI instead of accumulating.
 */
@Service
@ConditionalOnClass(RabbitTemplate.class)
public class AgentMessageService {

    private static final Logger logger = LoggerFactory.getLogger(AgentMessageService.class);

    private final RabbitTemplate rabbitTemplate;

    // Store response handlers for streaming individual responses as they arrive
    private final Map<String, ResponseHandler> activeHandlers = new ConcurrentHashMap<>();

    // Timeout for agent responses (30 seconds as specified in AGENTS.md)
    private static final int RESPONSE_TIMEOUT_SECONDS = 30;

    public AgentMessageService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Response handler for streaming individual responses as they arrive.
     */
    public static class ResponseHandler {
        private final Consumer<AgentResponse> onResponse;
        private final Consumer<Throwable> onError;
        private final Runnable onComplete;
        private final CompletableFuture<Void> completionFuture;
        private volatile boolean isCompleted = false;

        public ResponseHandler(Consumer<AgentResponse> onResponse,
                               Consumer<Throwable> onError,
                               Runnable onComplete) {
            this.onResponse = onResponse;
            this.onError = onError;
            this.onComplete = onComplete;
            this.completionFuture = new CompletableFuture<>();
        }

        public void handleResponse(AgentResponse response) {
            if (!isCompleted) {
                onResponse.accept(response);

                // Check if this is the final response
                if (Boolean.TRUE.equals(response.isComplete())) {
                    complete();
                }
            }
        }

        public void handleError(Throwable error) {
            if (!isCompleted) {
                isCompleted = true;
                onError.accept(error);
                completionFuture.completeExceptionally(error);
            }
        }

        public void complete() {
            if (!isCompleted) {
                isCompleted = true;
                onComplete.run();
                completionFuture.complete(null);
            }
        }

        public CompletableFuture<Void> getCompletionFuture() {
            return completionFuture;
        }

        public boolean isCompleted() {
            return isCompleted;
        }
    }

    /**
     * Sends a request to an agent and streams responses as they arrive.
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
    public CompletableFuture<Void> sendAgentRequest(String agentType, String prompt, String userId,
                                                    Consumer<AgentResponse> onResponse,
                                                    Consumer<Throwable> onError,
                                                    Runnable onComplete) {
        String correlationId = UUID.randomUUID().toString();

        // Create response handler
        ResponseHandler handler = new ResponseHandler(onResponse, onError, onComplete);
        activeHandlers.put(correlationId, handler);

        // Set up timeout handling
        handler.getCompletionFuture()
                .orTimeout(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((result, throwable) -> {
                    // Clean up handler regardless of outcome
                    activeHandlers.remove(correlationId);

                    if (throwable != null) {
                        logger.warn("Agent request timed out or failed for correlationId: {}", correlationId, throwable);
                        if (!handler.isCompleted()) {
                            handler.handleError(throwable);
                        }
                    } else {
                        logger.info("Successfully completed request for correlationId: {}", correlationId);
                    }
                });

        try {
            // Create and send the request
            AgentRequest request = AgentRequest.create(correlationId, agentType, prompt, userId);

            String routingKey = getRequestRoutingKey(agentType);
            rabbitTemplate.convertAndSend(RabbitMQConfig.AGENT_EXCHANGE, routingKey, request);

            logger.info("Sent agent request with correlationId: {} to agentType: {}", correlationId, agentType);

        } catch (Exception e) {
            logger.error("Failed to send agent request", e);
            activeHandlers.remove(correlationId);
            handler.handleError(e);
        }

        return handler.getCompletionFuture();
    }

    /**
     * Convenience method for backward compatibility.
     * Returns a CompletableFuture with the first response only.
     */
    public CompletableFuture<AgentResponse> sendAgentRequest(String agentType, String prompt, String userId) {
        CompletableFuture<AgentResponse> firstResponseFuture = new CompletableFuture<>();

        sendAgentRequest(agentType, prompt, userId,
                response -> {
                    // Complete with the first response for backward compatibility
                    if (!firstResponseFuture.isDone()) {
                        firstResponseFuture.complete(response);
                    }
                },
                error -> {
                    if (!firstResponseFuture.isDone()) {
                        firstResponseFuture.completeExceptionally(error);
                    }
                },
                () -> {
                    // Complete with null if no responses were received
                    if (!firstResponseFuture.isDone()) {
                        firstResponseFuture.complete(null);
                    }
                }
        );

        return firstResponseFuture;
    }

    /**
     * Handles incoming agent responses from the reply queue.
     * Streams each response immediately to registered handlers.
     *
     * @param response The agent response received from the queue
     */
    @RabbitListener(queues = RabbitMQConfig.AGENT_REPLY_QUEUE)
    public void handleAgentResponse(AgentResponse response) {
        String correlationId = response.correlationId();
        logger.info("Received agent response with correlationId: {}, isComplete: {}, isPartial: {}",
                correlationId, response.isComplete(), response.isPartial());

        ResponseHandler handler = activeHandlers.get(correlationId);

        if (handler != null) {
            // Stream this response immediately to the handler
            handler.handleResponse(response);
            logger.debug("Streamed response to handler for correlationId: {}", correlationId);
        } else {
            logger.warn("Received response for unknown correlationId: {} (may have timed out or completed)",
                    correlationId);
        }
    }

    /**
     * Gets the routing key for agent requests based on agent type.
     *
     * @param agentType The type of agent
     * @return The routing key for the request
     */
    private String getRequestRoutingKey(String agentType) {
        return switch (agentType.toLowerCase()) {
            case "reviewer" -> RabbitMQConfig.REVIEWER_REQUEST_ROUTING_KEY;
            default -> agentType.toLowerCase() + ".request";
        };
    }

    /**
     * Gets the current connection status for monitoring.
     *
     * @return Status information about the message queue connection
     */
    public Map<String, Object> getConnectionStatus() {
        return Map.of(
                "connectionStatus", "connected", // TODO: Implement actual connection monitoring
                "activeHandlers", activeHandlers.size()
        );
    }

    /**
     * Clears any active handlers (useful for cleanup).
     */
    public void clearActiveHandlers() {
        activeHandlers.forEach((correlationId, handler) -> {
            if (!handler.isCompleted()) {
                handler.handleError(new RuntimeException("Service shutdown"));
            }
        });
        activeHandlers.clear();
        logger.info("Cleared all active agent response handlers");
    }

    /**
     * Gets the number of active response handlers.
     *
     * @return Number of active handlers
     */
    public int getActiveHandlerCount() {
        return activeHandlers.size();
    }
}