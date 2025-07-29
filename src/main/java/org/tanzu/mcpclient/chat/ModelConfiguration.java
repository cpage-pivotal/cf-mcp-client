package org.tanzu.mcpclient.chat;

import io.pivotal.cfenv.boot.genai.GenaiLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for models, supporting both GenAI Locator and traditional Spring AI auto-configuration
 */
@Configuration
public class ModelConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ModelConfiguration.class);

    /**
     * Primary ChatModel bean using GenAI Locator when available
     */
    @Bean
    @Primary
    @ConditionalOnBean(GenaiLocator.class)
    public ChatModel genaiLocatorChatModel(GenaiLocator genaiLocator) {
        try {
            ChatModel chatModel = genaiLocator.getFirstAvailableChatModel();
            logger.info("Created ChatModel from GenAI Locator: {}", chatModel.getClass().getSimpleName());
            return chatModel;
        } catch (Exception e) {
            logger.error("Failed to create ChatModel from GenAI Locator", e);
            throw new IllegalStateException("No chat models available via GenAI Locator", e);
        }
    }

    /**
     * Primary EmbeddingModel bean using GenAI Locator when available
     */
    @Bean
    @Primary
    @ConditionalOnBean(GenaiLocator.class)
    public EmbeddingModel genaiLocatorEmbeddingModel(GenaiLocator genaiLocator) {
        try {
            EmbeddingModel embeddingModel = genaiLocator.getFirstAvailableEmbeddingModel();
            logger.info("Created EmbeddingModel from GenAI Locator: {}", embeddingModel.getClass().getSimpleName());
            return embeddingModel;
        } catch (Exception e) {
            logger.warn("No embedding models available via GenAI Locator: {}", e.getMessage());
            return null; // Allow null embedding model - vector store features will be disabled
        }
    }

    /**
     * Fallback ChatModel validation when GenAI Locator is not available
     */
    @Bean
    @ConditionalOnMissingBean(GenaiLocator.class)
    public ChatModelValidator chatModelValidator(@Autowired(required = false) ChatModel chatModel) {
        if (chatModel == null) {
            logger.warn("No ChatModel available and no GenAI Locator configured. " +
                    "Please either bind to a GenAI service or configure Spring AI properties.");
            throw new IllegalStateException(
                    "No ChatModel available. Either configure GenAI service binding or Spring AI properties.");
        }
        logger.info("Using fallback ChatModel: {}", chatModel.getClass().getSimpleName());
        return new ChatModelValidator();
    }

    /**
     * Configuration info bean for diagnostics
     */
    @Bean
    public ModelConfigurationInfo modelConfigurationInfo(
            @Autowired(required = false) GenaiLocator genaiLocator,
            @Autowired(required = false) ChatModel chatModel,
            @Autowired(required = false) EmbeddingModel embeddingModel) {

        return new ModelConfigurationInfo(
                genaiLocator != null,
                chatModel != null ? chatModel.getClass().getSimpleName() : "None",
                embeddingModel != null ? embeddingModel.getClass().getSimpleName() : "None"
        );
    }

    /**
     * Simple validator class to ensure ChatModel is available
     */
    public static class ChatModelValidator {
        // Empty class, just used as a marker bean
    }

    /**
     * Configuration information record
     */
    public record ModelConfigurationInfo(
            boolean genaiLocatorAvailable,
            String chatModelType,
            String embeddingModelType
    ) {
        public void logConfiguration() {
            logger.info("Model Configuration: GenAI Locator={}, ChatModel={}, EmbeddingModel={}",
                    genaiLocatorAvailable, chatModelType, embeddingModelType);
        }
    }
}