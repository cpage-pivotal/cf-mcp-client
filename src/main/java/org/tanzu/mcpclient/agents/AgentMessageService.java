package org.tanzu.mcpclient.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import org.tanzu.mcpclient.messaging.RabbitMQAvailableCondition;
import org.tanzu.mcpclient.messaging.RabbitMQConfig;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Service for handling agent message queue operations.
 * Manages correlation IDs and response routing as specified in AGENTS.md.
 * Only enabled when RabbitMQ is available.
 *
 * Updated to stream individual responses immediately to the UI instead of accumulating.
 */
@Service
@Conditional(RabbitMQAvailableCondition.class)
public class AgentMessageService implements AgentMessageServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(AgentMessageService.class);

    private final RabbitTemplate rabbitTemplate;

    // Store response handlers for streaming individual responses as they arrive
    private final Map<String, ResponseHandler> activeHandlers = new ConcurrentHashMap<>();

    // Timeout for agent responses (30 seconds as specified in AGENTS.md)
    private static final int RESPONSE_TIMEOUT_SECONDS = 30;

    public AgentMessageService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        logger.info("RabbitMQ-based AgentMessageService initialized");
    }

    /**
     * Response handler class for managing streaming responses.
     */
    public static class ResponseHandler {
        private final Consumer<AgentResponse> onResponse;
        private final Consumer<Throwable> onError;
        private final Runnable onComplete;
        private final CompletableFuture<Void> completionFuture;
        private volatile boolean completed = false;

        public ResponseHandler(Consumer<AgentResponse> onResponse,
                               Consumer<Throwable> onError,
                               Runnable onComplete) {
            this.onResponse = onResponse;
            this.onError = onError;
            this.onComplete = onComplete;
            this.completionFuture = new CompletableFuture<>();
        }

        public void handleResponse(AgentResponse response) {
            if (!completed) {
                try {
                    onResponse.accept(response);
                } catch (Exception e) {
                    handleError(e);
                }
            }
        }

        public void handleError(Throwable error) {
            if (!completed) {
                completed = true;
                try {
                    onError.accept(error);
                } catch (Exception e) {
                    logger.error("Error in error handler", e);
                }
                completionFuture.completeExceptionally(error);
            }
        }

        public void handleCompletion() {
            if (!completed) {
                completed = true;
                try {
                    onComplete.run();
                } catch (Exception e) {
                    logger.error("Error in completion handler", e);
                }
                completionFuture.complete(null);
            }
        }

        public CompletableFuture<Void> getCompletionFuture() {
            return completionFuture;
        }

        public boolean isCompleted() {
            return completed;
        }
    }

    @Override
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
     * Handles incoming agent responses from the reply queue.
     * Streams each response immediately to registered handlers.
     */
    @RabbitListener(queues = RabbitMQConfig.AGENT_REPLY_QUEUE)
    public void handleAgentResponse(AgentResponse response) {
        String correlationId = response.correlationId();
        logger.info("Received agent response with correlationId: {}, isComplete: {}",
                correlationId, response.isComplete());

        ResponseHandler handler = activeHandlers.get(correlationId);

        if (handler != null) {
            // Stream this response immediately to the handler
            handler.handleResponse(response);
            logger.debug("Streamed response to handler for correlationId: {}", correlationId);

            // If this is the final response, complete the conversation
            if (Boolean.TRUE.equals(response.isComplete())) {
                handler.handleCompletion();
                activeHandlers.remove(correlationId);
                logger.info("Agent conversation completed - CorrelationId: {}", correlationId);
            }
        } else {
            logger.warn("Received response for unknown correlationId: {} (may have timed out or completed)",
                    correlationId);
        }
    }

    /**
     * Gets the routing key for agent requests based on agent type.
     */
    private String getRequestRoutingKey(String agentType) {
        return switch (agentType.toLowerCase()) {
            case "reviewer" -> RabbitMQConfig.REVIEWER_REQUEST_ROUTING_KEY;
            default -> agentType.toLowerCase() + ".request";
        };
    }

    @Override
    public Map<String, Object> getConnectionStatus() {
        // Clean up any completed handlers
        activeHandlers.entrySet().removeIf(entry -> entry.getValue().isCompleted());

        return Map.of(
                "connectionStatus", "connected",
                "activeHandlers", activeHandlers.size(),
                "implementation", "rabbitmq"
        );
    }

}