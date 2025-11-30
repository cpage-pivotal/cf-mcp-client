package org.tanzu.mcpclient.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST controller for A2A (Agent to Agent) operations.
 * Provides endpoints for sending messages to A2A agents and receiving responses.
 * Uses the A2A Java SDK types (io.a2a.spec.*) directly.
 */
@RestController
@RequestMapping("/a2a")
public class A2AController {

    private static final Logger logger = LoggerFactory.getLogger(A2AController.class);

    private final A2AConfiguration a2aConfiguration;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public A2AController(A2AConfiguration a2aConfiguration, ObjectMapper objectMapper) {
        this.a2aConfiguration = a2aConfiguration;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a message to an A2A agent and returns the response.
     *
     * @param request Contains the service name and message text
     * @return Response containing success status, agent name, response text, or error
     */
    @PostMapping("/send-message")
    public ResponseEntity<SendMessageResponse> sendMessage(@RequestBody SendMessageRequest request) {
        logger.debug("Received send-message request for service: {}", request.serviceName());

        try {
            // Find agent service by service name
            A2AAgentService agentService = a2aConfiguration.getAgentServices().stream()
                    .filter(service -> service.getServiceName().equals(request.serviceName()))
                    .findFirst()
                    .orElse(null);

            if (agentService == null) {
                logger.warn("Agent service not found: {}", request.serviceName());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new SendMessageResponse(
                                false,
                                null,
                                null,
                                "Agent service not found: " + request.serviceName()
                        ));
            }

            // Check if agent is healthy
            if (!agentService.isHealthy()) {
                logger.warn("Agent service is unhealthy: {}", request.serviceName());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new SendMessageResponse(
                                false,
                                agentService.getAgentCard() != null ? agentService.getAgentCard().name() : "Unknown",
                                null,
                                "Agent is unhealthy: " + agentService.getErrorMessage()
                        ));
            }

            // Send message to agent - returns SDK Message or Task
            Object response = agentService.sendMessage(request.messageText());

            // Extract response text from SDK object
            String responseText = extractResponseText(response);

            logger.info("Successfully sent message to agent {} and received response",
                    agentService.getAgentCard().name());

            return ResponseEntity.ok(new SendMessageResponse(
                    true,
                    agentService.getAgentCard().name(),
                    responseText,
                    null
            ));

        } catch (IllegalStateException e) {
            // Agent is unhealthy
            logger.error("Agent is unhealthy: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new SendMessageResponse(
                            false,
                            null,
                            null,
                            "Agent is unhealthy: " + e.getMessage()
                    ));

        } catch (Exception e) {
            // Agent communication failed
            logger.error("Failed to communicate with agent: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new SendMessageResponse(
                            false,
                            null,
                            null,
                            "Failed to communicate with agent: " + e.getMessage()
                    ));
        }
    }

    /**
     * Extracts text from the SDK response object (Message or Task).
     *
     * @param response The SDK response object (Message or Task)
     * @return Extracted text from the response
     */
    private String extractResponseText(Object response) {
        if (response == null) {
            return "";
        }

        // Handle SDK Task
        if (response instanceof Task task) {
            if (task.getStatus() != null && task.getStatus().message() != null) {
                return extractTextFromMessage(task.getStatus().message());
            }
            logger.debug("Task has no status message");
            return "";
        }

        // Handle SDK Message
        if (response instanceof Message message) {
            return extractTextFromMessage(message);
        }

        // Fallback: return string representation
        logger.warn("Unknown response type: {}", response.getClass().getName());
        return response.toString();
    }

    /**
     * Extracts text content from an SDK Message.
     * Concatenates all TextPart elements.
     *
     * @param message SDK Message object
     * @return Concatenated text from all text parts
     */
    private String extractTextFromMessage(Message message) {
        if (message == null || message.getParts() == null || message.getParts().isEmpty()) {
            return "";
        }

        StringBuilder textBuilder = new StringBuilder();
        for (Part<?> part : message.getParts()) {
            if (part instanceof TextPart textPart) {
                if (!textBuilder.isEmpty()) {
                    textBuilder.append("\n");
                }
                textBuilder.append(textPart.getText());
            }
        }

        return textBuilder.toString();
    }

    /**
     * Sends a message to an A2A agent with streaming support for status updates.
     * Uses SSE to stream task status updates and final results as they arrive.
     *
     * @param serviceName The Cloud Foundry service name of the agent
     * @param messageText The text message to send to the agent
     * @return SseEmitter that streams status updates
     */
    @GetMapping(value = "/stream-message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@RequestParam("serviceName") String serviceName,
                                    @RequestParam("messageText") String messageText) {
        logger.debug("Received stream-message request for service: {}", serviceName);

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        executor.execute(() -> {
            try {
                // Find agent service by service name
                A2AAgentService agentService = a2aConfiguration.getAgentServices().stream()
                        .filter(service -> service.getServiceName().equals(serviceName))
                        .findFirst()
                        .orElse(null);

                if (agentService == null) {
                    logger.warn("Agent service not found: {}", serviceName);
                    sendError(emitter, "Agent service not found: " + serviceName);
                    return;
                }

                // Check if agent is healthy
                if (!agentService.isHealthy()) {
                    logger.warn("Agent service is unhealthy: {}", serviceName);
                    sendError(emitter, "Agent is unhealthy: " + agentService.getErrorMessage());
                    return;
                }

                String agentName = agentService.getAgentCard().name();

                // Subscribe to streaming responses from agent (SDK TaskUpdate events)
                agentService.sendMessageStreaming(messageText)
                        .subscribe(
                                taskUpdate -> {
                                    try {
                                        // Build status update from SDK Task
                                        A2AModels.StatusUpdate statusUpdate = buildStatusUpdate(taskUpdate, agentName);

                                        // Send status update as JSON SSE event
                                        String jsonData = objectMapper.writeValueAsString(statusUpdate);
                                        String eventType = statusUpdate.type();

                                        emitter.send(SseEmitter.event()
                                                .data(jsonData)
                                                .name(eventType));

                                        logger.debug("[A2A] [{}] Sent SSE event: type={}, state={}",
                                                agentName, eventType, statusUpdate.state());

                                    } catch (IOException e) {
                                        logger.error("[A2A] [{}] Error sending SSE event", agentName, e);
                                        emitter.completeWithError(e);
                                    }
                                },
                                error -> {
                                    logger.error("[A2A] [{}] Streaming error: {}", agentName, error.getMessage(), error);
                                    sendError(emitter, "Streaming error: " + error.getMessage());
                                },
                                () -> {
                                    try {
                                        logger.debug("[A2A] [{}] Streaming completed", agentName);
                                        emitter.send(SseEmitter.event()
                                                .name("close")
                                                .data(""));
                                        emitter.complete();
                                    } catch (IOException e) {
                                        emitter.completeWithError(e);
                                    }
                                }
                        );

            } catch (Exception e) {
                logger.error("Failed to initiate streaming: {}", e.getMessage(), e);
                sendError(emitter, "Failed to initiate streaming: " + e.getMessage());
            }
        });

        return emitter;
    }

    /**
     * Builds a StatusUpdate from an SDK TaskUpdate.
     * Extracts task state, status message, and response text.
     */
    private A2AModels.StatusUpdate buildStatusUpdate(A2AAgentService.TaskUpdate taskUpdate, String agentName) {
        Task task = taskUpdate.task();
        boolean isFinal = taskUpdate.isFinal();

        if (task.getStatus() == null) {
            logger.warn("[A2A] [{}] Received task update with null status: taskId={}, state={}",
                    agentName, task.getId(), task.getStatus() != null ? task.getStatus().state() : "null");
            return new A2AModels.StatusUpdate("status", "unknown", "Status is null", null, agentName);
        }

        String state = task.getStatus().state().asString();
        String statusMessage = null;
        String responseText = null;

        logger.debug("[A2A] [{}] Building status update: taskId={}, state={}, final={}, hasMessage={}",
                agentName, task.getId(), state, isFinal, task.getStatus().message() != null);

        // Determine event type based on final flag and state
        boolean isFinalState = isFinal
                || task.getStatus().state() == TaskState.COMPLETED
                || task.getStatus().state() == TaskState.FAILED
                || task.getStatus().state() == TaskState.REJECTED
                || task.getStatus().state() == TaskState.CANCELED;

        String eventType = isFinalState ? "result" : "status";

        // Extract message text if available
        if (task.getStatus().message() != null) {
            String text = extractTextFromMessage(task.getStatus().message());

            if (isFinalState) {
                responseText = text;  // Final result
                logger.info("[A2A] [{}] Final result received: state={}, textLength={}",
                        agentName, state, text != null ? text.length() : 0);
            } else {
                statusMessage = text;  // Intermediate status
                logger.debug("[A2A] [{}] Status message: {}", agentName, statusMessage);
            }
        } else {
            logger.debug("[A2A] [{}] No message in status for state={}", agentName, state);
        }

        return new A2AModels.StatusUpdate(eventType, state, statusMessage, responseText, agentName);
    }

    /**
     * Sends an error event via SSE and completes the emitter.
     */
    private void sendError(SseEmitter emitter, String errorMessage) {
        try {
            A2AModels.StatusUpdate errorUpdate = new A2AModels.StatusUpdate(
                    "error", "failed", null, errorMessage, null
            );
            String errorJson = objectMapper.writeValueAsString(errorUpdate);
            emitter.send(SseEmitter.event()
                    .data(errorJson)
                    .name("error"));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    /**
     * Request DTO for sending a message to an A2A agent.
     *
     * @param serviceName The Cloud Foundry service name of the agent
     * @param messageText The text message to send to the agent
     */
    public record SendMessageRequest(
            String serviceName,
            String messageText
    ) {}

    /**
     * Response DTO for the send-message endpoint.
     *
     * @param success Whether the message was sent successfully
     * @param agentName The name of the agent (from agent card)
     * @param responseText The text response from the agent
     * @param error Error message if the operation failed
     */
    public record SendMessageResponse(
            boolean success,
            String agentName,
            String responseText,
            String error
    ) {}
}
