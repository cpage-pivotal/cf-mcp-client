package org.tanzu.mcpclient.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing communication with a single A2A agent.
 * Handles agent card fetching, message sending via JSON-RPC, and health status tracking.
 */
public class A2AAgentService {
    private static final Logger logger = LoggerFactory.getLogger(A2AAgentService.class);

    private final String serviceName;
    private final String agentCardUri;
    private final RestClient restClient;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private AgentCard agentCard;
    private boolean healthy;
    private String errorMessage;

    /**
     * Constructor initializes the service and fetches the agent card.
     *
     * @param serviceName Cloud Foundry service name
     * @param agentCardUri URL to agent card (typically /.well-known/agent.json)
     * @param restClientBuilder Spring RestClient.Builder
     * @param webClientBuilder Spring WebClient.Builder for SSE streaming
     * @param objectMapper Jackson ObjectMapper for JSON serialization
     */
    public A2AAgentService(String serviceName, String agentCardUri,
                          RestClient.Builder restClientBuilder,
                          WebClient.Builder webClientBuilder,
                          ObjectMapper objectMapper) {
        this.serviceName = serviceName;
        this.agentCardUri = agentCardUri;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
        this.webClient = webClientBuilder.build();
        this.healthy = false;
        this.errorMessage = null;

        // Initialize agent card on construction
        initializeAgentCard();
    }

    /**
     * Fetches the agent card from the URI and parses it.
     * Sets healthy status based on success/failure.
     */
    private void initializeAgentCard() {
        logger.debug("[A2A] [{}] [INIT] Fetching agent card from: {}", serviceName, agentCardUri);

        try {
            String agentCardJson = restClient.get()
                    .uri(agentCardUri)
                    .retrieve()
                    .body(String.class);

            if (agentCardJson == null || agentCardJson.trim().isEmpty()) {
                this.errorMessage = "Agent card response is empty";
                this.healthy = false;
                logger.warn("[A2A] [{}] [INIT] {}", serviceName, errorMessage);
                return;
            }

            this.agentCard = objectMapper.readValue(agentCardJson, AgentCard.class);
            this.healthy = true;
            this.errorMessage = null;

            logger.info("[A2A] [{}] [INIT] Successfully loaded agent card for '{}' (version: {}, protocol: {})",
                    serviceName,
                    agentCard.name(),
                    agentCard.version(),
                    agentCard.protocolVersion());

        } catch (Exception e) {
            this.errorMessage = "Failed to fetch agent card: " + e.getMessage();
            this.healthy = false;
            logger.error("[A2A] [{}] [INIT] {}", serviceName, errorMessage, e);
        }
    }

    /**
     * Sends a text message to the A2A agent using JSON-RPC protocol.
     *
     * @param messageText The text message to send
     * @return JSON-RPC response from the agent
     * @throws IllegalStateException if agent is unhealthy
     * @throws RuntimeException if message send fails
     */
    public A2AModels.JsonRpcResponse sendMessage(String messageText) {
        if (!healthy) {
            throw new IllegalStateException("Agent is unhealthy: " + errorMessage);
        }

        logger.debug("[A2A] [{}] [SEND] Sending message to agent", agentCard.name());

        try {
            // Create message parts with text content
            A2AModels.TextPart textPart = new A2AModels.TextPart(messageText);
            List<A2AModels.Part> parts = List.of(textPart);

            // Generate unique message ID
            String messageId = UUID.randomUUID().toString();

            // Build Message object with user role
            A2AModels.Message message = new A2AModels.Message("user", parts, messageId);

            // Create message send configuration (blocking mode, text output)
            A2AModels.MessageSendConfiguration configuration = new A2AModels.MessageSendConfiguration(
                    List.of("text"),
                    true
            );

            // Build JSON-RPC request parameters
            Map<String, Object> params = Map.of(
                    "message", message,
                    "configuration", configuration
            );

            // Wrap in JSON-RPC request with method "message/send"
            A2AModels.JsonRpcRequest request = new A2AModels.JsonRpcRequest(
                    1,
                    "message/send",
                    params
            );

            // Convert request to JSON
            String requestJson = objectMapper.writeValueAsString(request);
            logger.debug("[A2A] [{}] [SEND] Request payload: {}", agentCard.name(), requestJson);

            // POST to agent URL
            long startTime = System.currentTimeMillis();
            String responseJson = restClient.post()
                    .uri(agentCard.url())
                    .header("Content-Type", "application/json")
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);

            long duration = System.currentTimeMillis() - startTime;

            // Parse JSON-RPC response
            A2AModels.JsonRpcResponse response = objectMapper.readValue(responseJson, A2AModels.JsonRpcResponse.class);

            if (response.error() != null) {
                logger.error("[A2A] [{}] [SEND] Agent returned error: {} - {}",
                        agentCard.name(),
                        response.error().code(),
                        response.error().message());
                throw new RuntimeException("Agent error: " + response.error().message());
            }

            logger.info("[A2A] [{}] [RESPONSE] Received response in {}ms", agentCard.name(), duration);
            return response;

        } catch (Exception e) {
            logger.error("[A2A] [{}] [SEND] Failed to send message: {}", agentCard.name(), e.getMessage(), e);
            throw new RuntimeException("Failed to send message to agent: " + e.getMessage(), e);
        }
    }

    // Getter methods

    public String getServiceName() {
        return serviceName;
    }

    public String getAgentCardUri() {
        return agentCardUri;
    }

    public AgentCard getAgentCard() {
        return agentCard;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sends a message to the A2A agent using streaming (SSE) for real-time status updates.
     *
     * @param messageText The text message to send
     * @return Flux of SendStreamingMessageResponse containing status updates and final result
     * @throws IllegalStateException if agent is unhealthy
     */
    public Flux<A2AModels.SendStreamingMessageResponse> sendMessageStreaming(String messageText) {
        if (!healthy) {
            return Flux.error(new IllegalStateException("Agent is unhealthy: " + errorMessage));
        }

        logger.debug("[A2A] [{}] [STREAM] Starting streaming message to agent", agentCard.name());

        try {
            // Create message parts with text content
            A2AModels.TextPart textPart = new A2AModels.TextPart(messageText);
            List<A2AModels.Part> parts = List.of(textPart);

            // Generate unique message ID
            String messageId = UUID.randomUUID().toString();

            // Build Message object with user role
            A2AModels.Message message = new A2AModels.Message("user", parts, messageId);

            // Create message send configuration (NON-blocking mode for streaming, text output)
            A2AModels.MessageSendConfiguration configuration = new A2AModels.MessageSendConfiguration(
                    List.of("text"),
                    false  // blocking = false for streaming
            );

            // Build JSON-RPC request parameters
            Map<String, Object> params = Map.of(
                    "message", message,
                    "configuration", configuration
            );

            // Wrap in JSON-RPC request with method "message/stream"
            A2AModels.JsonRpcRequest request = new A2AModels.JsonRpcRequest(
                    1,
                    "message/stream",  // Use streaming method
                    params
            );

            // Convert request to JSON
            String requestJson = objectMapper.writeValueAsString(request);
            logger.debug("[A2A] [{}] [STREAM] Request payload: {}", agentCard.name(), requestJson);

            // POST to agent URL and stream SSE responses
            return webClient.post()
                    .uri(agentCard.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                    .mapNotNull(event -> {
                        try {
                            String data = event.data();
                            if (data == null || data.isEmpty()) {
                                return null;
                            }

                            logger.debug("[A2A] [{}] [STREAM] Received SSE event: {}", agentCard.name(), data);

                            // Parse the JSON-RPC response
                            A2AModels.JsonRpcResponse jsonRpcResponse = objectMapper.readValue(data, A2AModels.JsonRpcResponse.class);

                            if (jsonRpcResponse.error() != null) {
                                logger.error("[A2A] [{}] [STREAM] Agent returned error: {} - {}",
                                        agentCard.name(),
                                        jsonRpcResponse.error().code(),
                                        jsonRpcResponse.error().message());
                                throw new RuntimeException("Agent error: " + jsonRpcResponse.error().message());
                            }

                            // Extract the result as a SendStreamingMessageResponse
                            Object result = jsonRpcResponse.result();
                            if (result instanceof Map<?, ?> resultMap) {
                                // Convert Map to SendStreamingMessageResponse
                                String resultJson = objectMapper.writeValueAsString(resultMap);
                                return objectMapper.readValue(resultJson, A2AModels.SendStreamingMessageResponse.class);
                            }

                            return null;
                        } catch (Exception e) {
                            logger.error("[A2A] [{}] [STREAM] Error parsing SSE event: {}", agentCard.name(), e.getMessage(), e);
                            throw new RuntimeException("Error parsing streaming response: " + e.getMessage(), e);
                        }
                    })
                    .doOnComplete(() -> logger.info("[A2A] [{}] [STREAM] Streaming completed", agentCard.name()))
                    .doOnError(error -> logger.error("[A2A] [{}] [STREAM] Streaming error: {}", agentCard.name(), error.getMessage(), error));

        } catch (Exception e) {
            logger.error("[A2A] [{}] [STREAM] Failed to initiate streaming: {}", agentCard.name(), e.getMessage(), e);
            return Flux.error(new RuntimeException("Failed to send streaming message to agent: " + e.getMessage(), e));
        }
    }
}
