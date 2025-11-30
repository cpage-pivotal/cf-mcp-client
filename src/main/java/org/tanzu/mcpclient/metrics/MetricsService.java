package org.tanzu.mcpclient.metrics;

import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.tanzu.mcpclient.a2a.A2AAgentService;
import org.tanzu.mcpclient.a2a.A2AConfigurationEvent;
import org.tanzu.mcpclient.chat.ChatConfigurationEvent;
import org.tanzu.mcpclient.document.DocumentConfigurationEvent;
import org.tanzu.mcpclient.prompt.McpPrompt;
import org.tanzu.mcpclient.prompt.PromptConfigurationEvent;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service that collects and provides platform metrics including models, MCP servers, and prompts.
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

    private int totalPrompts = 0;
    private int serversWithPrompts = 0;
    private boolean promptsAvailable = false;
    private Map<String, List<McpPrompt>> promptsByServer = Map.of();

    private List<A2AAgentService> a2aAgentServices = List.of();

    public MetricsService() {
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
    public void handlePromptConfigurationEvent(PromptConfigurationEvent event) {
        this.totalPrompts = event.getTotalPrompts();
        this.serversWithPrompts = event.getServersWithPrompts();
        this.promptsAvailable = event.isAvailable();
        this.promptsByServer = event.getPromptsByServer();
        logger.debug("Updated prompt metrics: total={}, servers={}, available={}",
                totalPrompts, serversWithPrompts, promptsAvailable);
    }

    @EventListener
    public void handleA2AConfigurationEvent(A2AConfigurationEvent event) {
        this.a2aAgentServices = event.getAgentServices() != null ? event.getAgentServices() : List.of();
        logger.debug("Updated A2A metrics: agents={}", a2aAgentServices.size());
    }

    public Metrics getMetrics(String conversationId) {
        logger.debug("Retrieving metrics for conversation: {}", conversationId);

        PromptMetrics promptMetrics = new PromptMetrics(
                this.totalPrompts,
                this.serversWithPrompts,
                this.promptsAvailable,
                this.promptsByServer
        );

        List<A2AAgent> a2aAgents = buildA2AAgentsList();

        return new Metrics(
                conversationId,
                this.chatModel,
                this.embeddingModel,
                this.vectorStoreName,
                this.mcpServersWithHealth.toArray(new McpServer[0]),
                promptMetrics,
                a2aAgents
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
            PromptMetrics prompts,
            List<A2AAgent> a2aAgents
    ) {}

    public record PromptMetrics(
            int totalPrompts,
            int serversWithPrompts,
            boolean available,
            Map<String, List<McpPrompt>> promptsByServer
    ) {}

    public record A2AAgent(
            String serviceName,
            String agentName,
            String description,
            String version,
            String agentCardUri,
            boolean healthy,
            String errorMessage,
            AgentCapabilities capabilities
    ) {}
}