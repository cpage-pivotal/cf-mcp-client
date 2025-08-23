package org.tanzu.mcpclient.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import org.tanzu.mcpclient.messaging.RabbitMQNotAvailableCondition;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * In-memory fallback implementation of agent messaging when RabbitMQ is not available.
 * Provides mock responses for testing and graceful degradation.
 */
@Service
@Conditional(RabbitMQNotAvailableCondition.class)
public class InMemoryAgentMessageService implements AgentMessageServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryAgentMessageService.class);

    public InMemoryAgentMessageService() {
        logger.warn("RabbitMQ not available - using in-memory agent message service fallback");
    }

    @Override
    public CompletableFuture<Void> sendAgentRequest(String agentType, String prompt, String userId,
                                                    Consumer<AgentResponse> onResponse,
                                                    Consumer<Throwable> onError,
                                                    Runnable onComplete) {

        String correlationId = UUID.randomUUID().toString();
        logger.info("Sending mock agent request - Agent: {}, CorrelationId: {}, UserId: {}",
                agentType, correlationId, userId);

        // Simulate async processing
        return CompletableFuture.runAsync(() -> {
            try {
                // Simulate processing delay
                Thread.sleep(500);

                // Send mock streaming response
                var mockResponse = generateMockResponse(agentType, prompt, correlationId);
                onResponse.accept(mockResponse);

                // Simulate completion
                Thread.sleep(200);
                onComplete.run();

                logger.info("Mock agent response completed - CorrelationId: {}", correlationId);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                onError.accept(new RuntimeException("Mock agent processing interrupted", e));
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    @Override
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

    @Override
    public Map<String, Object> getConnectionStatus() {
        return Map.of(
                "connectionStatus", "in-memory-fallback",
                "activeHandlers", 0,
                "mode", "mock"
        );
    }

    @Override
    public void clearActiveHandlers() {
        logger.info("In-memory service - no active handlers to clear");
    }

    @Override
    public int getActiveHandlerCount() {
        return 0;
    }

    /**
     * Generates a mock response based on the agent type and input message.
     */
    private AgentResponse generateMockResponse(String agentType, String prompt, String correlationId) {
        String mockContent = switch (agentType.toLowerCase()) {
            case "reviewer" -> generateReviewerResponse(prompt);
            case "analyzer" -> generateAnalyzerResponse(prompt);
            case "summarizer" -> generateSummarizerResponse(prompt);
            default -> generateGenericResponse(agentType, prompt);
        };

        return new AgentResponse(
                correlationId,
                agentType,
                mockContent,
                true, // isComplete
                System.currentTimeMillis(),
                null // metadata
        );
    }

    private String generateReviewerResponse(String prompt) {
        return String.format("""
            **Mock Reviewer Response** (RabbitMQ not available)
            
            I've reviewed your request: "%s"
            
            **Key Points:**
            - This is a simulated response since RabbitMQ is not connected
            - In production, this would be handled by the actual reviewer agent
            - The application is running in graceful degradation mode
            
            **Recommendations:**
            - Start RabbitMQ server for full agent functionality
            - Check RabbitMQ configuration in application.properties
            
            *Note: This is a mock response for testing purposes.*
            """, prompt.length() > 100 ? prompt.substring(0, 100) + "..." : prompt);
    }

    private String generateAnalyzerResponse(String prompt) {
        return String.format("""
            **Mock Analysis** (RabbitMQ not available)
            
            Analysis of: "%s"
            
            **Simulated Metrics:**
            - Complexity: Medium
            - Confidence: Mock data
            - Processing time: ~500ms (simulated)
            
            *This is a fallback response when RabbitMQ is unavailable.*
            """, prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt);
    }

    private String generateSummarizerResponse(String prompt) {
        return String.format("""
            **Mock Summary** (RabbitMQ not available)
            
            Summary of your request with %d characters:
            - Topic appears to be: General inquiry
            - Simulated processing complete
            - Full agent functionality requires RabbitMQ connection
            
            *This is a mock summary for graceful degradation.*
            """, prompt.length());
    }

    private String generateGenericResponse(String agentType, String prompt) {
        return String.format("""
            **Mock %s Response** (RabbitMQ not available)
            
            Received request: "%s"
            
            This is a simulated response since the message queue is not available.
            To enable full agent functionality, please ensure RabbitMQ is running.
            
            *Mock response generated at %s*
            """,
                agentType,
                prompt.length() > 80 ? prompt.substring(0, 80) + "..." : prompt,
                java.time.LocalDateTime.now());
    }
}