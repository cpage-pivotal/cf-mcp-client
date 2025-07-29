package org.tanzu.mcpclient.chat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.pivotal.cfenv.boot.genai.GenaiLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.tanzu.mcpclient.document.DocumentService;
import org.tanzu.mcpclient.util.McpClientFactory;
import reactor.core.publisher.Flux;

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
    private final GenaiLocator genaiLocator;

    @Value("classpath:/prompts/system-prompt.st")
    private Resource systemChatPrompt;

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    public ChatService(@Autowired(required = false) GenaiLocator genaiLocator,
                       @Autowired(required = false) ChatModel chatModel,
                       BaseChatMemoryAdvisor memoryAdvisor,
                       List<String> mcpServiceURLs,
                       VectorStore vectorStore,
                       McpClientFactory mcpClientFactory) {

        this.genaiLocator = genaiLocator;
        this.mcpServiceURLs = mcpServiceURLs;
        this.vectorStore = vectorStore;
        this.mcpClientFactory = mcpClientFactory;

        // Use GenAI Locator if available, otherwise fall back to injected ChatModel
        ChatModel selectedChatModel = selectChatModel(chatModel);

        // Build ChatClient with the selected model
        ChatClient.Builder chatClientBuilder = ChatClient.builder(selectedChatModel);
        chatClientBuilder = chatClientBuilder.defaultAdvisors(memoryAdvisor, new SimpleLoggerAdvisor());
        this.chatClient = chatClientBuilder.build();

        logger.info("ChatService initialized with chat model: {}",
                selectedChatModel.getClass().getSimpleName());
    }

    /**
     * Select the appropriate chat model using GenAI Locator if available,
     * otherwise fall back to the injected ChatModel
     */
    private ChatModel selectChatModel(ChatModel fallbackChatModel) {
        if (genaiLocator != null) {
            try {
                ChatModel chatModel = genaiLocator.getFirstAvailableChatModel();
                logger.info("Using chat model from GenAI Locator: {}",
                        getModelInfo(chatModel));
                return chatModel;
            } catch (Exception e) {
                logger.warn("Failed to get chat model from GenAI Locator, falling back to injected model: {}",
                        e.getMessage());
            }
        }

        if (fallbackChatModel != null) {
            logger.info("Using fallback injected chat model: {}",
                    fallbackChatModel.getClass().getSimpleName());
            return fallbackChatModel;
        }

        throw new IllegalStateException(
                "No chat model available. Either GenAI Locator must be configured or a ChatModel must be injected.");
    }

    /**
     * Get available chat models (for metrics/debugging)
     */
    public List<String> getAvailableChatModels() {
        if (genaiLocator != null) {
            try {
                return genaiLocator.getModelNamesByCapability("CHAT");
            } catch (Exception e) {
                logger.debug("Error getting available chat models: {}", e.getMessage());
            }
        }
        return List.of();
    }

    /**
     * Get the name/info of the currently selected model
     */
    public String getCurrentChatModelInfo() {
        if (genaiLocator != null) {
            try {
                List<String> chatModels = genaiLocator.getModelNamesByCapability("CHAT");
                return chatModels.isEmpty() ? "Unknown" : chatModels.get(0);
            } catch (Exception e) {
                logger.debug("Error getting current chat model info: {}", e.getMessage());
            }
        }
        return "Unknown";
    }

    private String getModelInfo(ChatModel chatModel) {
        // Try to extract useful information about the model
        // This might need to be adapted based on the actual ChatModel implementation
        String className = chatModel.getClass().getSimpleName();
        return className;
    }

    /**
     * Updated method to handle multiple document IDs
     */
    public Flux<String> chatStream(String chat, String conversationId, List<String> documentIds) {
        try (Stream<McpSyncClient> mcpSyncClients = createAndInitializeMcpClients()) {
            ToolCallbackProvider[] toolCallbackProviders = mcpSyncClients
                    .map(SyncMcpToolCallbackProvider::new)
                    .toArray(ToolCallbackProvider[]::new);

            logger.info("CHAT STREAM REQUEST: conversationID = {}, documentIds = {}", conversationId, documentIds);
            return buildAndExecuteStreamChatRequest(chat, conversationId, documentIds, toolCallbackProviders);
        }
    }

    private Flux<String> buildAndExecuteStreamChatRequest(String chat, String conversationId, List<String> documentIds,
                                                          ToolCallbackProvider[] toolCallbackProviders) {

        ChatClient.ChatClientRequestSpec spec = chatClient.
                prompt().
                user(chat).
                system(systemChatPrompt).
                toolCallbacks(toolCallbackProviders);

        if (vectorStore != null && documentIds != null && !documentIds.isEmpty()) {
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

    private Stream<McpSyncClient> createAndInitializeMcpClients() {
        return mcpServiceURLs.stream()
                .map(mcpClientFactory::createMcpSyncClient)
                .peek(McpSyncClient::initialize);
    }
}