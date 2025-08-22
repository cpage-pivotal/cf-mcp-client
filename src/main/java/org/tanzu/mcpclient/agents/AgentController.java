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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST controller for agent interactions.
 * Provides endpoints for sending requests to agents and receiving responses via SSE.
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
     * Sends a request to an agent and streams the response via SSE.
     * Follows the same pattern as the existing chat endpoint.
     *
     * @param agentType The type of agent to send the request to
     * @param prompt The prompt to send to the agent
     * @param request HTTP request for session management
     * @return SSE emitter for streaming the agent response
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

                // Send request to agent via message queue
                CompletableFuture<AgentResponse> responseFuture =
                        agentMessageService.sendAgentRequest(agentType, prompt, userId);

                // Handle the response
                responseFuture
                        .thenAccept(response -> {
                            try {
                                // Send the agent response via SSE - handle null values safely
                                Map<String, Object> payload = new HashMap<>();
                                payload.put("content", response.content() != null ? response.content() : "");
                                payload.put("agentType", response.agentType() != null ? response.agentType() : "reviewer");
                                payload.put("timestamp", response.timestamp() != 0 ? response.timestamp() : System.currentTimeMillis());
                                payload.put("isComplete", response.isComplete() != null ? response.isComplete() : true);

                                String jsonData = objectMapper.writeValueAsString(payload);

                                emitter.send(SseEmitter.event()
                                        .data(jsonData)
                                        .name("agent-message"));

                                // Send completion event
                                emitter.send(SseEmitter.event()
                                        .name("close")
                                        .data(""));

                                emitter.complete();

                            } catch (IOException e) {
                                handleAgentError(emitter, e, userId);
                            }
                        })
                        .exceptionally(throwable -> {
                            handleAgentError(emitter, throwable, userId);
                            return null;
                        });

            } catch (Exception e) {
                handleAgentError(emitter, e, userId);
            }
        });

        return emitter;
    }

    /**
     * Gets the current status of agent messaging system.
     *
     * @return Status information including connection status and pending requests
     */
    @GetMapping("/status")
    public Map<String, Object> getAgentStatus() {
        if (agentMessageService == null) {
            return Map.of(
                    "connectionStatus", "disconnected",
                    "pendingRequests", 0,
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