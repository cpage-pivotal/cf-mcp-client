package org.tanzu.mcpclient.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * REST controller for agent interactions.
 * Provides endpoints for sending requests to agents and receiving responses via SSE.
 * Updated to stream individual responses immediately as they arrive.
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);

    private final AgentMessageService agentMessageService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentController(@Autowired(required = false) AgentMessageService agentMessageService) {
        this.agentMessageService = agentMessageService;
    }

    /**
     * Sends a request to an agent and streams each response via SSE as it arrives.
     * Each agent action (e.g., craftStory, reviewStory) sends a separate SSE event immediately.
     *
     * @param agentType The type of agent to send the request to
     * @param prompt The prompt to send to the agent
     * @param request HTTP request for session management
     * @return SSE emitter for streaming the agent responses
     */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter agentChat(@RequestParam("agentType") String agentType,
                                @RequestParam("prompt") String prompt,
                                HttpServletRequest request) {

        String userId = request.getSession().getId();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        logger.info("Agent chat request: agentType={}, userId={}", agentType, userId);

        executor.execute(() -> {
            try {
                if (agentMessageService == null) {
                    handleAgentError(emitter, new RuntimeException("Agent messaging system not available - RabbitMQ not configured"), userId);
                    return;
                }

                // Track response sequence for client-side handling
                AtomicInteger responseCounter = new AtomicInteger(0);

                // Send request to agent with streaming handlers
                agentMessageService.sendAgentRequest(agentType, prompt, userId,
                        // onResponse: stream each response immediately
                        response -> {
                            try {
                                int responseIndex = responseCounter.incrementAndGet();
                                sendAgentResponseEvent(emitter, response, responseIndex);
                                logger.debug("Streamed response {} for correlationId: {}",
                                        responseIndex, response.correlationId());
                            } catch (IOException e) {
                                logger.error("Failed to stream response", e);
                                handleAgentError(emitter, e, userId);
                            }
                        },
                        // onError: handle any errors
                        error -> {
                            logger.error("Agent request failed for userId: {}", userId, error);
                            handleAgentError(emitter, error, userId);
                        },
                        // onComplete: close the SSE connection
                        () -> {
                            try {
                                logger.info("Agent conversation completed for userId: {}", userId);

                                // Send completion event
                                emitter.send(SseEmitter.event()
                                        .name("close")
                                        .data(""));

                                emitter.complete();
                            } catch (IOException e) {
                                logger.error("Failed to send completion event", e);
                                emitter.completeWithError(e);
                            }
                        }
                );

            } catch (Exception e) {
                handleAgentError(emitter, e, userId);
            }
        });

        return emitter;
    }

    /**
     * Sends a single agent response as an SSE event immediately.
     *
     * @param emitter The SSE emitter
     * @param response The agent response to send
     * @param responseIndex The 1-based index of this response in the sequence
     */
    private void sendAgentResponseEvent(SseEmitter emitter, AgentResponse response, int responseIndex)
            throws IOException {

        // Create payload with response data
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", response.content() != null ? response.content() : "");
        payload.put("agentType", response.agentType() != null ? response.agentType() : "reviewer");
        payload.put("timestamp", response.timestamp() != 0 ? response.timestamp() : System.currentTimeMillis());
        payload.put("correlationId", response.correlationId());
        payload.put("responseIndex", responseIndex);

        // Pass through completion flags from the agent
        payload.put("isPartial", response.isPartial() != null ? response.isPartial() : false);
        payload.put("isComplete", response.isComplete() != null ? response.isComplete() : true);

        // Add metadata if available
        if (response.metadata() != null) {
            payload.put("metadata", response.metadata());
        }

        String jsonData = objectMapper.writeValueAsString(payload);

        emitter.send(SseEmitter.event()
                .data(jsonData)
                .name("agent-message"));

        logger.debug("Sent SSE event for response {} with correlationId: {}",
                responseIndex, response.correlationId());
    }

    /**
     * Gets the current status of agent messaging system.
     *
     * @return Status information including connection status and active handlers
     */
    @GetMapping("/status")
    public Map<String, Object> getAgentStatus() {
        if (agentMessageService == null) {
            return Map.of(
                    "connectionStatus", "disconnected",
                    "activeHandlers", 0,
                    "message", "RabbitMQ not configured"
            );
        }
        return agentMessageService.getConnectionStatus();
    }

    /**
     * Handles errors in agent communication by sending error events via SSE.
     *
     * @param emitter The SSE emitter
     * @param error The error that occurred
     * @param userId The user ID for context
     */
    private void handleAgentError(SseEmitter emitter, Throwable error, String userId) {
        try {
            logger.error("Agent communication error for user: {}", userId, error);

            // Create error response
            Map<String, Object> errorPayload = Map.of(
                    "error", true,
                    "message", "Failed to communicate with agent: " + error.getMessage(),
                    "timestamp", System.currentTimeMillis()
            );

            String errorJson = objectMapper.writeValueAsString(errorPayload);
            emitter.send(SseEmitter.event()
                    .data(errorJson)
                    .name("error"));

            emitter.complete();

        } catch (Exception e) {
            logger.error("Failed to send error response", e);
            emitter.completeWithError(e);
        }
    }
}