package org.tanzu.mcpclient.chat;

import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.tanzu.mcpclient.document.DocumentService;
import org.tanzu.mcpclient.model.ModelDiscoveryService;
import org.tanzu.mcpclient.mcp.McpClientFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final List<String> mcpServiceURLs;
    private final McpClientFactory mcpClientFactory;
    private final ModelDiscoveryService modelDiscoveryService; // Add this field

    @Value("classpath:/prompts/system-prompt.st")
    private Resource systemChatPrompt;

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    // Update constructor to inject ModelDiscoveryService
    public ChatService(ChatClient.Builder chatClientBuilder, BaseChatMemoryAdvisor memoryAdvisor,
                       List<String> mcpServiceURLs, VectorStore vectorStore, McpClientFactory mcpClientFactory,
                       ModelDiscoveryService modelDiscoveryService) {
        chatClientBuilder = chatClientBuilder.defaultAdvisors(memoryAdvisor, new SimpleLoggerAdvisor());
        this.chatClient = chatClientBuilder.build();

        this.mcpServiceURLs = mcpServiceURLs;
        this.vectorStore = vectorStore;
        this.mcpClientFactory = mcpClientFactory;
        this.modelDiscoveryService = modelDiscoveryService; // Store the service
    }

    /**
     * Updated method to handle multiple document IDs
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
        return mcpServiceURLs.stream()
                .map(this::createProtocolAwareMcpClient)  // ✅ Use protocol-aware method instead of deprecated one
                .peek(McpSyncClient::initialize);
    }

    /**
     * ✅ Creates MCP client with correct protocol based on service binding.
     */
    private McpSyncClient createProtocolAwareMcpClient(String serverUrl) {
        // Simple fix: Check if this is your Streamable HTTP server
        if (serverUrl.contains("streamable-time.apps.tas-ndc.kuhn-labs.com")) {
            logger.info("✅ Creating Streamable HTTP client for: {}", serverUrl);
            return mcpClientFactory.createStreamableClient(
                    serverUrl,
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(5)
            );
        } else {
            // Use SSE for other servers (backward compatibility)
            logger.info("Creating SSE client for: {}", serverUrl);
            return mcpClientFactory.createSseClient(
                    serverUrl,
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(5)
            );
        }
    }

    private Flux<String> buildAndExecuteStreamChatRequest(String chat, String conversationId, List<String> documentIds,
                                                          ToolCallbackProvider[] toolCallbackProviders) {

        ChatClient.ChatClientRequestSpec spec = chatClient.
                prompt().
                user(chat).
                system(systemChatPrompt).
                toolCallbacks(toolCallbackProviders);

        if (documentIds != null && !documentIds.isEmpty()) {
            spec = addDocumentSearchCapabilities(spec, documentIds);
        }

        spec = spec.advisors(a -> a.param(CONVERSATION_ID, conversationId));

        return spec.stream().content()
                .filter(Objects::nonNull);
    }

    /**
     * Updated method to handle multiple document IDs with OR filter expressions
     */
    private ChatClient.ChatClientRequestSpec addDocumentSearchCapabilities(
            ChatClient.ChatClientRequestSpec spec,
            List<String> documentIds) {

        Advisor questionAnswerAdvisor = new QuestionAnswerAdvisor(this.vectorStore);

        // Build OR filter expression for multiple documents
        String filterExpression = buildDocumentFilterExpression(documentIds);

        logger.debug("Using document filter expression: {}", filterExpression);

        return spec.advisors(questionAnswerAdvisor)
                .advisors(advisorSpec ->
                        advisorSpec.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, filterExpression));
    }

    /**
     * Builds a filter expression for multiple document IDs using OR logic
     * Format: "documentId == 'doc1' OR documentId == 'doc2' OR documentId == 'doc3'"
     */
    private String buildDocumentFilterExpression(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return "";
        }

        // Filter out null or empty document IDs
        List<String> validDocumentIds = documentIds.stream()
                .filter(id -> id != null && !id.trim().isEmpty())
                .toList();

        if (validDocumentIds.isEmpty()) {
            return "";
        }

        // For single document, use simple equality
        if (validDocumentIds.size() == 1) {
            return DocumentService.DOCUMENT_ID + " == '" + validDocumentIds.get(0) + "'";
        }

        // For multiple documents, use OR expressions
        return validDocumentIds.stream()
                .map(docId -> DocumentService.DOCUMENT_ID + " == '" + docId + "'")
                .collect(Collectors.joining(" OR "));
    }
}