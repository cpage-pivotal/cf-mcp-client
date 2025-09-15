package org.tanzu.mcpclient.chat;

import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.tanzu.mcpclient.mcp.McpClientFactory;
import org.tanzu.mcpclient.mcp.McpServerService;
import org.tanzu.mcpclient.mcp.ProtocolType;
import org.tanzu.mcpclient.model.ModelDiscoveryService;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final List<McpServerService> mcpServerServices; // Changed from List<String> mcpServiceURLs
    private final McpClientFactory mcpClientFactory;
    private final ModelDiscoveryService modelDiscoveryService;

    @Value("classpath:/prompts/system-prompt.st")
    private Resource systemChatPrompt;

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    // Updated constructor to use McpServerService instead of URL strings
    public ChatService(ChatClient.Builder chatClientBuilder, BaseChatMemoryAdvisor memoryAdvisor,
                       List<McpServerService> mcpServerServices, VectorStore vectorStore,
                       McpClientFactory mcpClientFactory, ModelDiscoveryService modelDiscoveryService) {
        chatClientBuilder = chatClientBuilder.defaultAdvisors(memoryAdvisor, new SimpleLoggerAdvisor());
        this.chatClient = chatClientBuilder.build();

        this.mcpServerServices = mcpServerServices; // Changed from mcpServiceURLs
        this.vectorStore = vectorStore;
        this.mcpClientFactory = mcpClientFactory;
        this.modelDiscoveryService = modelDiscoveryService;
    }

    /**
     * Updated method to handle multiple document IDs with graceful degradation
     */
    public Flux<String> chatStream(String chat, String conversationId, List<String> documentIds) {
        // Validate chat model availability - this is where graceful degradation happens
        String chatModel = modelDiscoveryService.getChatModelName();
        if (chatModel == null || chatModel.isEmpty()) {
            logger.warn("Chat request attempted but no chat model configured");
            return Flux.error(new IllegalStateException("No chat model configured"));
        }

        try (Stream<McpSyncClient> mcpSyncClients = createAndInitializeMcpClients()) {
            ToolCallbackProvider[] toolCallbackProviders = mcpSyncClients
                    .map(SyncMcpToolCallbackProvider::new)
                    .toArray(ToolCallbackProvider[]::new);

            logger.info("CHAT STREAM REQUEST: conversationID = {}, documentIds = {}", conversationId, documentIds);
            return buildAndExecuteStreamChatRequest(chat, conversationId, documentIds, toolCallbackProviders);
        }
    }

    private Stream<McpSyncClient> createAndInitializeMcpClients() {
        return mcpServerServices.stream() // Changed from mcpServiceURLs.stream()
                .map(this::createProtocolAwareMcpClient) // Now takes McpServerService instead of String
                .peek(McpSyncClient::initialize);
    }

    /**
     * IMPROVED: Creates MCP client with correct protocol based on service configuration.
     * Uses modern pattern matching for sealed interface records.
     * No more hardcoded URL substring checking!
     */
    private McpSyncClient createProtocolAwareMcpClient(McpServerService serverService) {
        String serverUrl = serverService.getServerUrl();
        ProtocolType protocol = serverService.getProtocol();

        logger.info("Creating {} client for: {} ({})",
                protocol.displayName(), serverService.getName(), serverUrl);

        // Use pattern matching with sealed interface records
        return switch (protocol) {
            case ProtocolType.StreamableHttp streamableHttp -> mcpClientFactory.createStreamableClient(
                    serverUrl,
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(5)
            );
            case ProtocolType.SSE sse -> mcpClientFactory.createSseClient(
                    serverUrl,
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(5)
            );
            case ProtocolType.Legacy legacy -> mcpClientFactory.createSseClient(
                    serverUrl,
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(5)
            );
        };
    }

    private Flux<String> buildAndExecuteStreamChatRequest(String chat, String conversationId, List<String> documentIds,
                                                          ToolCallbackProvider[] toolCallbackProviders) {

        ChatClient.ChatClientRequestSpec spec = chatClient
                .prompt()
                .user(chat)
                .system(systemChatPrompt);

        // Add conversation context
        spec = spec.advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, conversationId));

        // Add document context if documents are provided
        if (documentIds != null && !documentIds.isEmpty()) {
            logger.debug("Adding document context for documents: {}", documentIds);

            // Use simple QuestionAnswerAdvisor with just VectorStore
            spec = spec.advisors(new QuestionAnswerAdvisor(vectorStore));
        }

        // Add MCP tools if available
        if (toolCallbackProviders.length > 0) {
            logger.debug("Adding {} MCP tool callback providers", toolCallbackProviders.length);
            spec = spec.toolCallbacks(toolCallbackProviders);
        }

        return spec.stream().content();
    }

    // Helper methods for backward compatibility and testing

    /**
     * Get all healthy MCP server URLs for backward compatibility
     */
    public List<String> getHealthyMcpServiceURLs() {
        return mcpServerServices.stream()
                .filter(service -> {
                    try {
                        // Quick health check
                        McpSyncClient client = createProtocolAwareMcpClient(service);
                        client.initialize();
                        return true;
                    } catch (Exception e) {
                        logger.debug("MCP server {} is unhealthy: {}", service.getName(), e.getMessage());
                        return false;
                    }
                })
                .map(McpServerService::getServerUrl)
                .collect(Collectors.toList());
    }

    /**
     * Get all configured MCP server services
     */
    public List<McpServerService> getMcpServerServices() {
        return List.copyOf(mcpServerServices);
    }
}