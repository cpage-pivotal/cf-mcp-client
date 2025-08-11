package org.tanzu.mcpclient.model;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

/**
 * Simplified Spring AI configuration using the Model Provider Abstraction pattern.
 * Uses CompositeModelProvider to orchestrate multiple model sources with priority-based selection.
 * This replaces the previous mixed configuration approach with a cleaner provider pattern.
 */
@Configuration
public class ModelConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ModelConfiguration.class);

    private final ModelDiscoveryService modelDiscoveryService;
    private final CompositeModelProvider compositeModelProvider;

    public ModelConfiguration(ModelDiscoveryService modelDiscoveryService,
                              CompositeModelProvider compositeModelProvider) {
        this.modelDiscoveryService = modelDiscoveryService;
        this.compositeModelProvider = compositeModelProvider;

        logger.info("SpringAIConfiguration initialized with CompositeModelProvider");
        logger.info("Available model providers: {}", compositeModelProvider.getProviderInfo());
    }

    /**
     * Creates OpenAI API client for property-based models.
     * This is used when models need to be created from traditional property configuration.
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenAiApi openAiApi() {
        // Use a representative model config (chat) for API client setup since both models share the same API
        GenaiModel config = modelDiscoveryService.getChatModelConfig();
        
        String apiKey = config.apiKey();
        String baseUrl = config.baseUrl();

        // For GenaiLocator models, we don't create the OpenAiApi since models are managed externally
        if (config.isFromGenaiLocator()) {
            logger.debug("GenaiLocator models detected - creating minimal OpenAiApi (not used by GenaiLocator models)");
            return OpenAiApi.builder()
                    .apiKey("managed-by-genai-locator")
                    .baseUrl("managed-by-genai-locator")
                    .build();
        }

        logger.debug("Creating OpenAiApi for property-based models with baseUrl={}, hasApiKey={}",
                baseUrl, apiKey != null && !apiKey.isEmpty());

        // Only create functional API client if we have proper credentials
        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("No valid API key found for property-based OpenAI models - models will be non-functional");
            apiKey = "no-api-key-configured"; // Avoid the "placeholder" that triggers specific OpenAI error
        }

        return OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public RetryTemplate retryTemplate() {
        return RetryUtils.DEFAULT_RETRY_TEMPLATE;
    }

    @Bean
    @ConditionalOnMissingBean
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.NOOP;
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolCallingManager toolCallingManager() {
        return ToolCallingManager.builder().build();
    }

    @Bean
    @ConditionalOnMissingBean
    ChatMemoryRepository chatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder().chatMemoryRepository(chatMemoryRepository).build();
    }

    /**
     * Creates ChatModel bean using CompositeModelProvider for provider abstraction.
     */
    @Bean
    @ConditionalOnMissingBean
    public ChatModel chatModel() {
        try {
            ChatModel chatModel = compositeModelProvider.getChatModel();
            logger.info("ChatModel successfully provided by CompositeModelProvider");
            return chatModel;
        } catch (IllegalStateException e) {
            logger.error("Failed to obtain ChatModel from any provider: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Creates EmbeddingModel bean using CompositeModelProvider for provider abstraction.
     */
    @Bean
    @ConditionalOnMissingBean
    public EmbeddingModel embeddingModel() {
        try {
            EmbeddingModel embeddingModel = compositeModelProvider.getEmbeddingModel();
            logger.info("EmbeddingModel successfully provided by CompositeModelProvider");
            return embeddingModel;
        } catch (IllegalStateException e) {
            logger.error("Failed to obtain EmbeddingModel from any provider: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Creates ChatClient.Builder bean. This must always be available for dependency injection.
     */
    @Bean
    @ConditionalOnMissingBean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        logger.debug("Creating ChatClient.Builder with ChatModel: {}", chatModel.getClass().getSimpleName());
        return ChatClient.builder(chatModel);
    }

}