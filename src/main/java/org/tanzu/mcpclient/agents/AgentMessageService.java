package org.tanzu.mcpclient.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Simplified agent message service.
 * Works with predefined agent types and doesn't require complex configuration.
 */
@Service
@Conditional(RabbitMQAvailableCondition.class)
public class AgentMessageService implements AgentMessageServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(AgentMessageService.class);

    private final RabbitTemplate rabbitTemplate;
    private final Map<String, ResponseHandler> activeHandlers = new ConcurrentHashMap<>();

    // Supported agent types - add more as needed
    private static final List<String> SUPPORTED_AGENT_TYPES = List.of(
            "reviewer", "editor", "translator", "summarizer"
    );

    private static final int RESPONSE_TIMEOUT_SECONDS = 30;

    public AgentMessageService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        logger.info("Simplified Agent Message Service initialized for agent types: {}", SUPPORTED_AGENT_TYPES);
    }

    @Override
    public CompletableFuture<Void> sendAgentRequest(String agentType, String prompt, String userId,
                                                    Consumer<AgentResponse> onResponse,
                                                    Consumer<Throwable> onError,
                                                    Runnable onComplete) {

        // Validate agent type is supported
        if (!SUPPORTED_AGENT_TYPES.contains(agentType.toLowerCase())) {
            var error = new IllegalArgumentException("Unsupported agent type: " + agentType +
                    ". Supported types: " + SUPPORTED_AGENT_TYPES);
            onError.accept(error);
            return CompletableFuture.failedFuture(error);
        }

        String correlationId = UUID.randomUUID().toString();
        ResponseHandler handler = new ResponseHandler(onResponse, onError, onComplete);
        activeHandlers.put(correlationId, handler);

        // Set up timeout
        handler.getCompletionFuture()
                .orTimeout(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((result, throwable) -> {
                    activeHandlers.remove(correlationId);
                    if (throwable != null && !handler.isCompleted()) {
                        handler.handleError(throwable);
                    }
                });

        try {
            // Create and send request
            AgentRequest request = AgentRequest.create(correlationId, agentType, prompt, userId);
            String routingKey = RabbitMQConfig.getRequestRoutingKey(agentType);

            rabbitTemplate.convertAndSend(RabbitMQConfig.AGENT_EXCHANGE, routingKey, request);

            logger.info("Sent request to agent type '{}' with correlationId: {}", agentType, correlationId);

        } catch (Exception e) {
            logger.error("Failed to send request to agent type '{}'", agentType, e);
            activeHandlers.remove(correlationId);
            handler.handleError(e);
        }

        return handler.getCompletionFuture();
    }

    /**
     * Generic response handler - called by specific listener methods.
     */
    public void handleAgentResponse(AgentResponse response) {
        String correlationId = response.correlationId();
        logger.info("Received response from agent '{}' with correlationId: {}",
                response.agentType(), correlationId);

        ResponseHandler handler = activeHandlers.get(correlationId);
        if (handler != null) {
            handler.handleResponse(response);

            if (Boolean.TRUE.equals(response.isComplete())) {
                handler.handleCompletion();
                activeHandlers.remove(correlationId);
            }
        } else {
            logger.warn("Received response for unknown correlationId: {}", correlationId);
        }
    }

    @Override
    public Map<String, Object> getConnectionStatus() {
        activeHandlers.entrySet().removeIf(entry -> entry.getValue().isCompleted());

        return Map.of(
                "connectionStatus", "connected",
                "activeHandlers", activeHandlers.size(),
                "implementation", "rabbitmq",
                "supportedAgentTypes", SUPPORTED_AGENT_TYPES
        );
    }

    /**
     * Response handler inner class for managing streaming responses.
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
                    // Log but don't throw
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
                    // Log but don't throw
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
}