package org.tanzu.mcpclient.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.TaskUpdateEvent;
import io.a2a.client.config.ClientConfig;
import io.a2a.client.http.A2ACardResolver;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfig;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Service for managing communication with a single A2A agent using the A2A Java SDK.
 * Handles agent card fetching, message sending, and health status tracking.
 */
public class A2AAgentService {
    private static final Logger logger = LoggerFactory.getLogger(A2AAgentService.class);
    private static final int MESSAGE_TIMEOUT_SECONDS = 120;

    private final String serviceName;
    private final String agentCardUri;
    private final ObjectMapper objectMapper;

    private AgentCard agentCard;
    private Client blockingClient;
    private Client streamingClient;
    private boolean healthy;
    private String errorMessage;

    /**
     * Constructor initializes the service and fetches the agent card.
     *
     * @param serviceName Cloud Foundry service name
     * @param agentCardUri URL to agent card (typically /.well-known/agent.json)
     * @param objectMapper Jackson ObjectMapper for JSON serialization
     */
    public A2AAgentService(String serviceName, String agentCardUri, ObjectMapper objectMapper) {
        this.serviceName = serviceName;
        this.agentCardUri = agentCardUri;
        this.objectMapper = objectMapper;
        this.healthy = false;
        this.errorMessage = null;

        // Initialize agent card and SDK client on construction
        initializeAgentCard();
    }

    /**
     * Fetches the agent card from the URI using the SDK's A2ACardResolver.
     * Sets healthy status based on success/failure and creates SDK clients.
     */
    private void initializeAgentCard() {
        logger.debug("[A2A] [{}] [INIT] Fetching agent card from: {}", serviceName, agentCardUri);

        try {
            // Use SDK's A2ACardResolver to fetch agent card
            A2ACardResolver resolver = new A2ACardResolver(agentCardUri);
            this.agentCard = resolver.getAgentCard();

            logger.info("[A2A] [{}] [INIT] Successfully loaded agent card for '{}' (version: {}, protocol: {})",
                    serviceName,
                    agentCard.name(),
                    agentCard.version(),
                    agentCard.protocolVersion());

            // Create SDK clients - one for blocking, one for streaming
            createClients();

            this.healthy = true;
            this.errorMessage = null;

        } catch (Exception e) {
            this.errorMessage = "Failed to fetch agent card: " + e.getMessage();
            this.healthy = false;
            logger.error("[A2A] [{}] [INIT] {}", serviceName, errorMessage, e);
        }
    }

    /**
     * Creates SDK clients for both blocking and streaming communication.
     */
    private void createClients() {
        try {
            // Create blocking client configuration
            ClientConfig blockingConfig = new ClientConfig.Builder()
                    .setAcceptedOutputModes(List.of("text"))
                    .setStreaming(false)  // Blocking mode
                    .build();

            // Create blocking client using SDK
            this.blockingClient = Client
                    .builder(agentCard)
                    .clientConfig(blockingConfig)
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                    .build();

            // Create streaming client configuration
            ClientConfig streamingConfig = new ClientConfig.Builder()
                    .setAcceptedOutputModes(List.of("text"))
                    .setStreaming(true)  // Streaming mode
                    .build();

            // Create streaming client using SDK
            this.streamingClient = Client
                    .builder(agentCard)
                    .clientConfig(streamingConfig)
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                    .build();

            logger.debug("[A2A] [{}] [INIT] SDK clients created successfully", serviceName);

        } catch (Exception e) {
            logger.error("[A2A] [{}] [INIT] Failed to create SDK clients: {}", serviceName, e.getMessage(), e);
            throw new RuntimeException("Failed to create SDK clients", e);
        }
    }

    /**
     * Sends a text message to the A2A agent using the SDK client (blocking mode).
     * Returns the SDK Message or Task directly.
     *
     * @param messageText The text message to send
     * @return SDK Message or Task response from the agent
     * @throws IllegalStateException if agent is unhealthy
     * @throws RuntimeException if message send fails
     */
    public Object sendMessage(String messageText) {
        if (!healthy) {
            throw new IllegalStateException("Agent is unhealthy: " + errorMessage);
        }

        logger.debug("[A2A] [{}] [SEND] Sending message to agent", agentCard.name());

        CompletableFuture<Object> responseFuture = new CompletableFuture<>();

        try {
            // Create event consumers to capture response
            List<BiConsumer<ClientEvent, AgentCard>> consumers = List.of(
                    (event, card) -> {
                        try {
                            logger.debug("[A2A] [{}] [SEND] Received event: {}", card.name(), event.getClass().getSimpleName());

                            if (event instanceof MessageEvent messageEvent) {
                                // Return SDK Message directly
                                responseFuture.complete(messageEvent.getMessage());

                            } else if (event instanceof TaskEvent taskEvent) {
                                // Return SDK Task directly
                                responseFuture.complete(taskEvent.getTask());
                            }
                        } catch (Exception e) {
                            logger.error("[A2A] [{}] [SEND] Error processing event: {}", card.name(), e.getMessage(), e);
                            responseFuture.completeExceptionally(e);
                        }
                    }
            );

            Consumer<Throwable> errorHandler = error -> {
                logger.error("[A2A] [{}] [SEND] Error from agent: {}", agentCard.name(), error.getMessage(), error);
                responseFuture.completeExceptionally(error);
            };

            // Create message using SDK Message.Builder
            Message message = new Message.Builder()
                    .role(Message.Role.USER)
                    .parts(new TextPart(messageText))
                    .build();

            long startTime = System.currentTimeMillis();

            // Send message using SDK client
            blockingClient.sendMessage(message, consumers, errorHandler, null);

            // Wait for response with timeout
            Object response = responseFuture.get(MESSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("[A2A] [{}] [RESPONSE] Received response in {}ms", agentCard.name(), duration);

            return response;

        } catch (TimeoutException e) {
            logger.error("[A2A] [{}] [SEND] Timeout waiting for response after {} seconds",
                    agentCard.name(), MESSAGE_TIMEOUT_SECONDS);
            throw new RuntimeException("Timeout waiting for agent response", e);
        } catch (InterruptedException | ExecutionException e) {
            logger.error("[A2A] [{}] [SEND] Failed to send message: {}", agentCard.name(), e.getMessage(), e);
            throw new RuntimeException("Failed to send message to agent: " + e.getMessage(), e);
        }
    }

    /**
     * Sends a message to the A2A agent using streaming for real-time status updates.
     * Returns a Flux of TaskUpdate events containing the SDK Task.
     *
     * @param messageText The text message to send
     * @return Flux of TaskUpdate records containing SDK Task and completion status
     * @throws IllegalStateException if agent is unhealthy
     */
    public Flux<TaskUpdate> sendMessageStreaming(String messageText) {
        if (!healthy) {
            return Flux.error(new IllegalStateException("Agent is unhealthy: " + errorMessage));
        }

        logger.debug("[A2A] [{}] [STREAM] Starting streaming message to agent", agentCard.name());

        // Create a Sinks.Many to emit events as they arrive
        Sinks.Many<TaskUpdate> sink = Sinks.many().multicast().onBackpressureBuffer();

        try {
            // Create event consumers to capture streaming updates
            List<BiConsumer<ClientEvent, AgentCard>> consumers = List.of(
                    (event, card) -> {
                        try {
                            logger.debug("[A2A] [{}] [STREAM] Received event: {}", card.name(), event.getClass().getSimpleName());

                            if (event instanceof TaskUpdateEvent updateEvent) {
                                // Emit task update (not final)
                                Task task = updateEvent.getTask();
                                sink.tryEmitNext(new TaskUpdate(task, false));

                            } else if (event instanceof TaskEvent taskEvent) {
                                // Emit final task event
                                Task task = taskEvent.getTask();
                                sink.tryEmitNext(new TaskUpdate(task, true));
                                sink.tryEmitComplete();

                            } else if (event instanceof MessageEvent messageEvent) {
                                // Direct message response (no task) - convert to task-like structure
                                Message msg = messageEvent.getMessage();
                                Task syntheticTask = createTaskFromMessage(msg);
                                sink.tryEmitNext(new TaskUpdate(syntheticTask, true));
                                sink.tryEmitComplete();
                            }
                        } catch (Exception e) {
                            logger.error("[A2A] [{}] [STREAM] Error processing event: {}", card.name(), e.getMessage(), e);
                            sink.tryEmitError(e);
                        }
                    }
            );

            Consumer<Throwable> errorHandler = error -> {
                logger.error("[A2A] [{}] [STREAM] Streaming error: {}", agentCard.name(), error.getMessage(), error);
                sink.tryEmitError(error);
            };

            // Create message using SDK Message.Builder
            Message message = new Message.Builder()
                    .role(Message.Role.USER)
                    .parts(new TextPart(messageText))
                    .build();

            // Send message using streaming client
            streamingClient.sendMessage(message, consumers, errorHandler, null);

            return sink.asFlux()
                    .doOnComplete(() -> logger.info("[A2A] [{}] [STREAM] Streaming completed", agentCard.name()))
                    .doOnError(error -> logger.error("[A2A] [{}] [STREAM] Streaming error: {}",
                            agentCard.name(), error.getMessage(), error));

        } catch (Exception e) {
            logger.error("[A2A] [{}] [STREAM] Failed to initiate streaming: {}", agentCard.name(), e.getMessage(), e);
            return Flux.error(new RuntimeException("Failed to send streaming message to agent: " + e.getMessage(), e));
        }
    }

    /**
     * Creates a synthetic Task from a Message for consistency in streaming responses.
     */
    private Task createTaskFromMessage(Message message) {
        // Create a TaskStatus with the message and COMPLETED state
        TaskStatus status = new TaskStatus(
                TaskState.COMPLETED,
                message,
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        // Create a Task with completed state
        return new Task.Builder()
                .id(message.getTaskId() != null ? message.getTaskId() : UUID.randomUUID().toString())
                .contextId(message.getContextId())
                .status(status)
                .history(List.of(message))
                .build();
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
     * Cleanup method to close SDK clients when service is no longer needed.
     */
    public void close() {
        try {
            if (blockingClient != null) {
                blockingClient.close();
            }
            if (streamingClient != null) {
                streamingClient.close();
            }
            logger.info("[A2A] [{}] [CLEANUP] Clients closed", serviceName);
        } catch (Exception e) {
            logger.error("[A2A] [{}] [CLEANUP] Error closing clients: {}", serviceName, e.getMessage(), e);
        }
    }

    /**
     * Record representing a task update from streaming responses.
     * Contains the SDK Task and whether this is the final update.
     *
     * @param task The SDK Task object
     * @param isFinal Whether this is the final update (task completed/failed)
     */
    public record TaskUpdate(Task task, boolean isFinal) {}
}
