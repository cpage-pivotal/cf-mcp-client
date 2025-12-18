package org.tanzu.mcpclient.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.tanzu.mcpclient.a2a.A2AAgentService;
import org.tanzu.mcpclient.a2a.A2AConfigurationEvent;
import org.tanzu.mcpclient.a2a.AgentCard;
import org.tanzu.mcpclient.chat.ChatConfigurationEvent;
import org.tanzu.mcpclient.document.DocumentConfigurationEvent;
import org.tanzu.mcpclient.memory.MemoryPreferenceService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service that collects and provides platform metrics including models and MCP servers.
 * This service listens to various configuration events and maintains current state
 * for monitoring and status display purposes.
 */
@Service
public class MetricsService {

    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);

    private String chatModel = "";
    private List<McpServer> mcpServersWithHealth = List.of();
    private String embeddingModel = "";
    private String vectorStoreName = "";

    private List<A2AAgentService> a2aAgentServices = List.of();

    private final MemoryPreferenceService memoryPreferenceService;

    public MetricsService(MemoryPreferenceService memoryPreferenceService) {
        this.memoryPreferenceService = memoryPreferenceService;
    }

    @EventListener
    public void handleChatConfigurationEvent(ChatConfigurationEvent event) {
        this.chatModel = event.getChatModel() != null ? event.getChatModel() : "";
        this.mcpServersWithHealth = event.getMcpServersWithHealth() != null ? event.getMcpServersWithHealth() : List.of();
        logger.debug("Updated chat metrics: model={}, mcpServers={}", chatModel, mcpServersWithHealth.size());
    }

    @EventListener
    public void handleDocumentConfigurationEvent(DocumentConfigurationEvent event) {
        this.embeddingModel = event.getEmbeddingModel() != null ? event.getEmbeddingModel() : "";
        this.vectorStoreName = event.getVectorStoreName() != null ? event.getVectorStoreName() : "";
        logger.debug("Updated document metrics: embedding={}, vectorStore={}", embeddingModel, vectorStoreName);
    }

    @EventListener
    public void handleA2AConfigurationEvent(A2AConfigurationEvent event) {
        this.a2aAgentServices = event.getAgentServices() != null ? event.getAgentServices() : List.of();
        logger.debug("Updated A2A metrics: agents={}", a2aAgentServices.size());
    }

    public Metrics getMetrics(String conversationId) {
        logger.debug("Retrieving metrics for conversation: {}", conversationId);

        List<A2AAgent> a2aAgents = buildA2AAgentsList();

        // Get the current memory type preference for this conversation
        String memoryType = memoryPreferenceService.getPreference(conversationId).name();

        return new Metrics(
                conversationId,
                this.chatModel,
                this.embeddingModel,
                this.vectorStoreName,
                this.mcpServersWithHealth.toArray(new McpServer[0]),
                a2aAgents,
                memoryType
        );
    }

    /**
     * Builds the list of A2A agents from agent services for metrics reporting.
     */
    private List<A2AAgent> buildA2AAgentsList() {
        return a2aAgentServices.stream()
                .map(service -> {
                    AgentCard card = service.getAgentCard();
                    return new A2AAgent(
                            service.getServiceName(),
                            card != null ? card.name() : "Unknown",
                            card != null ? card.description() : "",
                            card != null ? card.version() : "",
                            service.getAgentCardUri(),
                            service.isHealthy(),
                            service.getErrorMessage(),
                            card != null ? card.capabilities() : null
                    );
                })
                .collect(Collectors.toList());
    }

    public record Metrics(
            String conversationId,
            String chatModel,
            String embeddingModel,
            String vectorStoreName,
            McpServer[] mcpServers,
            List<A2AAgent> a2aAgents,
            String memoryType
    ) {}

    public record A2AAgent(
            String serviceName,
            String agentName,
            String description,
            String version,
            String agentCardUri,
            boolean healthy,
            String errorMessage,
            AgentCard.AgentCapabilities capabilities
    ) {}
}