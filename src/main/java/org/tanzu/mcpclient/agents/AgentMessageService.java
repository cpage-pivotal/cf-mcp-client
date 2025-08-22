package org.tanzu.mcpclient.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for handling agent message queue operations.
 * Manages correlation IDs and response routing as specified in AGENTS.md.
 */
@Service
@ConditionalOnClass(RabbitTemplate.class)
public class AgentMessageService {

    private static final Logger logger = LoggerFactory.getLogger(AgentMessageService.class);
    
    private final RabbitTemplate rabbitTemplate;
    
    // Store pending requests with correlation IDs
    private final Map<String, CompletableFuture<AgentResponse>> pendingRequests = new ConcurrentHashMap<>();
    
    // Timeout for agent responses (30 seconds as specified in AGENTS.md)
    private static final int RESPONSE_TIMEOUT_SECONDS = 30;

    public AgentMessageService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Sends a request to an agent and returns a CompletableFuture for the response.
     * Implements correlation ID management for async request/response.
     * 
     * @param agentType The type of agent (e.g., "reviewer")
     * @param prompt The prompt to send to the agent
     * @param userId The user ID making the request
     * @return CompletableFuture that will complete with the agent response
     */
    public CompletableFuture<AgentResponse> sendAgentRequest(String agentType, String prompt, String userId) {
        return sendAgentRequest(agentType, prompt, userId, null);
    }

    /**
     * Sends a request to an agent with additional context.
     * 
     * @param agentType The type of agent (e.g., "reviewer")
     * @param prompt The prompt to send to the agent
     * @param userId The user ID making the request
     * @param context Additional context for the request
     * @return CompletableFuture that will complete with the agent response
     */
    public CompletableFuture<AgentResponse> sendAgentRequest(String agentType, String prompt, String userId, Object context) {
        String correlationId = UUID.randomUUID().toString();
        CompletableFuture<AgentResponse> responseFuture = new CompletableFuture<>();
        
        // Store the pending request
        pendingRequests.put(correlationId, responseFuture);
        
        // Set up timeout handling
        responseFuture.orTimeout(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((response, throwable) -> {
                    // Clean up pending request regardless of outcome
                    pendingRequests.remove(correlationId);
                    
                    if (throwable != null) {
                        logger.warn("Agent request timed out or failed for correlationId: {}", correlationId, throwable);
                    }
                });

        try {
            // Create and send the request
            AgentRequest request = AgentRequest.create(correlationId, agentType, prompt, userId, context);
            
            String routingKey = getRequestRoutingKey(agentType);
            rabbitTemplate.convertAndSend(RabbitMQConfig.AGENT_EXCHANGE, routingKey, request);
            
            logger.info("Sent agent request with correlationId: {} to agentType: {}", correlationId, agentType);
            
        } catch (Exception e) {
            logger.error("Failed to send agent request", e);
            pendingRequests.remove(correlationId);
            responseFuture.completeExceptionally(e);
        }

        return responseFuture;
    }

    /**
     * Handles incoming agent responses from the reply queue.
     * Uses correlation ID to match responses to pending requests.
     * 
     * @param response The agent response received from the queue
     */
    @RabbitListener(queues = RabbitMQConfig.AGENT_REPLY_QUEUE)
    public void handleAgentResponse(AgentResponse response) {
        logger.info("Received agent response with correlationId: {}", response.correlationId());
        
        CompletableFuture<AgentResponse> pendingRequest = pendingRequests.get(response.correlationId());
        
        if (pendingRequest != null) {
            // Complete the pending request with the response
            pendingRequest.complete(response);
            logger.debug("Completed pending request for correlationId: {}", response.correlationId());
        } else {
            logger.warn("Received response for unknown correlationId: {}", response.correlationId());
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
                "pendingRequests", pendingRequests.size()
        );
    }

    /**
     * Clears any pending requests (useful for cleanup).
     */
    public void clearPendingRequests() {
        pendingRequests.forEach((correlationId, future) -> {
            if (!future.isDone()) {
                future.cancel(true);
            }
        });
        pendingRequests.clear();
        logger.info("Cleared all pending agent requests");
    }
}