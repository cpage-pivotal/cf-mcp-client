package org.tanzu.mcpclient.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.tanzu.mcpclient.chat.ChatConfigurationEvent;
import org.tanzu.mcpclient.document.DocumentConfigurationEvent;
import org.tanzu.mcpclient.prompt.McpPrompt;
import org.tanzu.mcpclient.prompt.PromptConfigurationEvent;

import java.util.List;
import java.util.Map;

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

    public Metrics getMetrics(String conversationId) {
        logger.debug("Retrieving metrics for conversation: {}", conversationId);

        PromptMetrics promptMetrics = new PromptMetrics(
                this.totalPrompts,
                this.serversWithPrompts,
                this.promptsAvailable,
                this.promptsByServer
        );

        return new Metrics(
                conversationId,
                this.chatModel,
                this.embeddingModel,
                this.vectorStoreName,
                this.mcpServersWithHealth.toArray(new McpServer[0]),
                promptMetrics
        );
    }

    public record Metrics(
            String conversationId,
            String chatModel,
            String embeddingModel,
            String vectorStoreName,
            McpServer[] mcpServers,
            PromptMetrics prompts
    ) {}

    public record PromptMetrics(
            int totalPrompts,
            int serversWithPrompts,
            boolean available,
            Map<String, List<McpPrompt>> promptsByServer
    ) {}
}