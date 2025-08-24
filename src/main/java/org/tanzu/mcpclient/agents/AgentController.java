package org.tanzu.mcpclient.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simplified agent controller that works with predefined agent types.
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);

    private final AgentMessageServiceInterface agentMessageService;
    private final SimpleAgentRegistryService agentRegistry;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentController(
            @Autowired(required = false) AgentMessageServiceInterface agentMessageService,
            SimpleAgentRegistryService agentRegistry) {
        this.agentMessageService = agentMessageService;
        this.agentRegistry = agentRegistry;
    }

    /**
     * Gets all available agent types.
     */
    @GetMapping
    public ResponseEntity<List<AgentInfo>> getAvailableAgents() {
        try {
            var agents = agentRegistry.getAvailableAgents();
            logger.info("Retrieved {} available agent types", agents.size());
            return ResponseEntity.ok(agents);
        } catch (Exception e) {
            logger.error("Failed to retrieve available agents", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Gets information about a specific agent type.
     */
    @GetMapping("/{agentType}")
    public ResponseEntity<AgentInfo> getAgent(@PathVariable String agentType) {
        try {
            var agent = agentRegistry.getAgent(agentType);
            if (agent.isPresent()) {
                return ResponseEntity.ok(agent.get());
            } else {
                logger.warn("Agent type not found: {}", agentType);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve agent type: {}", agentType, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Agent chat endpoint.
     */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter agentChat(@RequestParam("agentType") String agentType,
                                @RequestParam("prompt") String prompt,
                                HttpServletRequest request) {

        String userId = request.getSession().getId();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        logger.info("Agent chat request: agentType={}, userId={}", agentType, userId);

        // Validate agent type
        if (!agentRegistry.isAgentAvailable(agentType)) {
            logger.warn("Unknown agent type requested: {}", agentType);
            handleAgentError(emitter,
                    new IllegalArgumentException("Unknown agent type: " + agentType),
                    userId);
            return emitter;
        }

        executor.execute(() -> {
            try {
                if (agentMessageService == null) {
                    handleAgentError(emitter,
                            new RuntimeException("Agent messaging system not available"),
                            userId);
                    return;
                }

                AtomicInteger responseCounter = new AtomicInteger(0);

                agentMessageService.sendAgentRequest(agentType, prompt, userId,
                        // onResponse: stream each response immediately
                        response -> {
                            try {
                                int responseIndex = responseCounter.incrementAndGet();
                                sendAgentResponseEvent(emitter, response, responseIndex);
                                logger.debug("Streamed response {} for agent type: {}",
                                        responseIndex, agentType);
                            } catch (IOException e) {
                                logger.error("Failed to stream response from agent type: {}", agentType, e);
                                handleAgentError(emitter, e, userId);
                            }
                        },
                        // onError: handle any errors
                        error -> {
                            logger.error("Agent request failed for agent type: {}", agentType, error);
                            handleAgentError(emitter, error, userId);
                        },
                        // onComplete: close the SSE connection
                        () -> {
                            try {
                                logger.info("Agent conversation completed for agent type: {}", agentType);
                                emitter.send(SseEmitter.event().name("close").data(""));
                                emitter.complete();
                            } catch (IOException e) {
                                logger.error("Failed to send completion event for agent type: {}", agentType, e);
                                emitter.completeWithError(e);
                            }
                        }
                );

            } catch (Exception e) {
                logger.error("Unexpected error during agent chat for type: {}", agentType, e);
                handleAgentError(emitter, e, userId);
            }
        });

        return emitter;
    }

    /**
     * Gets system status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        try {
            Map<String, Object> status = new HashMap<>();

            // Get messaging service status
            if (agentMessageService != null) {
                status.putAll(agentMessageService.getConnectionStatus());
            } else {
                status.put("connectionStatus", "unavailable");
                status.put("implementation", "none");
            }

            // Add agent type information
            var agents = agentRegistry.getAvailableAgents();
            status.put("availableAgentTypes", agents.stream().map(AgentInfo::id).toList());
            status.put("agentCount", agents.size());

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("Failed to get system status", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // Helper methods
    private void sendAgentResponseEvent(SseEmitter emitter, AgentResponse response, int responseIndex)
            throws IOException {

        Map<String, Object> payload = new HashMap<>();
        payload.put("content", response.content() != null ? response.content() : "");
        payload.put("agentType", response.agentType() != null ? response.agentType() : "unknown");
        payload.put("timestamp", response.timestamp() != 0 ? response.timestamp() : System.currentTimeMillis());
        payload.put("correlationId", response.correlationId());
        payload.put("responseIndex", responseIndex);
        payload.put("isComplete", response.isComplete() != null ? response.isComplete() : false);

        if (response.metadata() != null) {
            payload.put("metadata", response.metadata());
        }

        // Add agent metadata
        var agent = agentRegistry.getAgent(response.agentType());
        if (agent.isPresent()) {
            payload.put("agentInfo", Map.of(
                    "name", agent.get().name(),
                    "description", agent.get().description(),
                    "icon", agent.get().icon() != null ? agent.get().icon() : "smart_toy",
                    "color", agent.get().color() != null ? agent.get().color() : "#0066CC"
            ));
        }

        String jsonData = objectMapper.writeValueAsString(payload);
        emitter.send(SseEmitter.event().name("agent-message").data(jsonData));
    }

    private void handleAgentError(SseEmitter emitter, Throwable error, String userId) {
        try {
            Map<String, Object> errorPayload = Map.of(
                    "error", true,
                    "message", error.getMessage() != null ? error.getMessage() : "Unknown error",
                    "timestamp", System.currentTimeMillis(),
                    "userId", userId
            );

            String jsonData = objectMapper.writeValueAsString(errorPayload);
            emitter.send(SseEmitter.event().name("error").data(jsonData));
            emitter.completeWithError(error);
        } catch (IOException e) {
            logger.error("Failed to send error event to SSE stream", e);
            emitter.completeWithError(e);
        }
    }
}