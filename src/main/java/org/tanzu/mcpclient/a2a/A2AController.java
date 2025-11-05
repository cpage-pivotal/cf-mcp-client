package org.tanzu.mcpclient.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for A2A (Agent to Agent) operations.
 * Provides endpoints for sending messages to A2A agents and receiving responses.
 */
@RestController
@RequestMapping("/a2a")
public class A2AController {

    private static final Logger logger = LoggerFactory.getLogger(A2AController.class);

    private final A2AConfiguration a2aConfiguration;
    private final ObjectMapper objectMapper;

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

            // Send message to agent
            A2AModels.JsonRpcResponse response = agentService.sendMessage(request.messageText());

            // Extract response text from JSON-RPC result
            String responseText = extractResponseText(response.result());

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
     * Extracts text from the JSON-RPC result object.
     * The result can be either a Task or a Message object.
     *
     * @param result The JSON-RPC result object
     * @return Extracted text from the result
     */
    private String extractResponseText(Object result) {
        if (result == null) {
            return "";
        }

        try {
            // Try to parse as Task first
            A2AModels.Task task = objectMapper.convertValue(result, A2AModels.Task.class);
            if (task != null && task.status() != null && task.status().message() != null) {
                return extractTextFromParts(task.status().message().parts());
            }
        } catch (Exception e) {
            logger.debug("Result is not a Task, trying Message");
        }

        try {
            // Try to parse as Message
            A2AModels.Message message = objectMapper.convertValue(result, A2AModels.Message.class);
            if (message != null && message.parts() != null) {
                return extractTextFromParts(message.parts());
            }
        } catch (Exception e) {
            logger.warn("Failed to parse result as Task or Message: {}", e.getMessage());
        }

        // Fallback: return string representation
        return result.toString();
    }

    /**
     * Extracts text content from a list of message parts.
     * Concatenates all TextPart elements.
     *
     * @param parts List of message parts
     * @return Concatenated text from all text parts
     */
    private String extractTextFromParts(List<A2AModels.Part> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }

        StringBuilder textBuilder = new StringBuilder();
        for (A2AModels.Part part : parts) {
            if (part instanceof A2AModels.TextPart textPart) {
                if (!textBuilder.isEmpty()) {
                    textBuilder.append("\n");
                }
                textBuilder.append(textPart.text());
            }
        }

        return textBuilder.toString();
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
