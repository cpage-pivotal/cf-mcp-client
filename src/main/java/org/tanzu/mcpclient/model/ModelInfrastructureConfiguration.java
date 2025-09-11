package org.tanzu.mcpclient.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.retry.support.RetryTemplate;

/**
 * Infrastructure configuration for model-related beans.
 * This is separated from ModelConfiguration to avoid circular dependencies.
 * These beans are created early and can be used by model providers.
 */
@Configuration
@Order(1) // Ensure this configuration is processed before ModelConfiguration
public class ModelInfrastructureConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ModelInfrastructureConfiguration.class);

    private final ModelDiscoveryService modelDiscoveryService;

    public ModelInfrastructureConfiguration(ModelDiscoveryService modelDiscoveryService) {
        this.modelDiscoveryService = modelDiscoveryService;
        logger.debug("ModelInfrastructureConfiguration initialized");
    }

    /**
     * Creates OpenAI API client specifically for chat models.
     * This is used when chat models need to be created from traditional property configuration.
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenAiApi chatOpenAiApi() {
        GenaiModel config = modelDiscoveryService.getChatModelConfig();
        logger.debug("Creating chatOpenAiApi for chat model with baseUrl={}", config.baseUrl());
        return createOpenAiApi(config, "chat");
    }

    /**
     * Creates OpenAI API client specifically for embedding models.
     * This is used when embedding models need to be created from traditional property configuration.
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenAiApi embeddingOpenAiApi() {
        GenaiModel config = modelDiscoveryService.getEmbeddingModelConfig();
        logger.debug("Creating embeddingOpenAiApi for embedding model with baseUrl={}", config.baseUrl());
        return createOpenAiApi(config, "embedding");
    }

    /**
     * Common OpenAI API client creation logic.
     * Handles both GenaiLocator and property-based model configurations.
     */
    private OpenAiApi createOpenAiApi(GenaiModel config, String modelType) {
        String apiKey = config.apiKey();
        String baseUrl = config.baseUrl();

        // For GenaiLocator models, we don't create the OpenAiApi since models are managed externally
        if (config.isFromGenaiLocator()) {
            logger.debug("GenaiLocator {} model detected - creating minimal OpenAiApi (not used by GenaiLocator models)", modelType);
            return OpenAiApi.builder()
                    .apiKey("managed-by-genai-locator")
                    .baseUrl("managed-by-genai-locator")
                    .build();
        }

        logger.debug("Creating OpenAiApi for property-based {} model with baseUrl={}, hasApiKey={}",
                modelType, baseUrl, apiKey != null && !apiKey.isEmpty());

        // Only create functional API client if we have proper credentials
        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("No valid API key found for property-based {} model - model will be non-functional", modelType);
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
}