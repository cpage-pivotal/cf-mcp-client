package org.tanzu.mcpclient.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Uses CompositeModelProvider to orchestrate multiple model sources with priority-based selection.
 * Infrastructure beans are now in ModelInfrastructureConfiguration to avoid circular dependencies.
 */
@Configuration
@Order(2) // Ensure this configuration is processed after ModelInfrastructureConfiguration
public class ModelConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ModelConfiguration.class);

    private final CompositeModelProvider compositeModelProvider;

    public ModelConfiguration(CompositeModelProvider compositeModelProvider) {
        this.compositeModelProvider = compositeModelProvider;

        logger.info("ModelConfiguration initialized with CompositeModelProvider");
        logger.info("Available model providers: {}", compositeModelProvider.getProviderInfo());
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