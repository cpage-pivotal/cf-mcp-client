package org.tanzu.mcpclient.chat;

import io.pivotal.cfenv.boot.genai.GenaiLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.document.MetadataMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for models, supporting both GenAI Locator and traditional Spring AI autoconfiguration
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
     * Fallback ChatModel using OpenAI when API key is configured and GenAI Locator is not available
     */
    @Bean
    @ConditionalOnMissingBean(GenaiLocator.class)
    @ConditionalOnProperty(name = {"spring.ai.openai.api-key", "spring.ai.openai.chat.api-key"})
    public ChatModel openAiChatModel(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.chat.api-key:}") String chatApiKey,
            @Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String model) {

        String effectiveApiKey = !chatApiKey.isEmpty() ? chatApiKey : apiKey;

        if (effectiveApiKey.isEmpty()) {
            logger.warn("No OpenAI API key configured, ChatModel will not be available");
            return null;
        }

        try {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .apiKey(effectiveApiKey)
                    .baseUrl(baseUrl)
                    .build();

            ChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(model)
                            .build())
                    .build();

            logger.info("Created OpenAI ChatModel with model: {}", model);
            return chatModel;
        } catch (Exception e) {
            logger.error("Failed to create OpenAI ChatModel", e);
            return null;
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
     * Fallback EmbeddingModel using OpenAI when API key is configured and GenAI Locator is not available
     */
    @Bean
    @ConditionalOnMissingBean(GenaiLocator.class)
    @ConditionalOnProperty(name = {"spring.ai.openai.api-key", "spring.ai.openai.embedding.api-key"})
    public EmbeddingModel openAiEmbeddingModel(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.embedding.api-key:}") String embeddingApiKey,
            @Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String model) {

        String effectiveApiKey = !embeddingApiKey.isEmpty() ? embeddingApiKey : apiKey;

        if (effectiveApiKey.isEmpty()) {
            logger.warn("No OpenAI API key configured, EmbeddingModel will not be available");
            return null;
        }

        try {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .apiKey(effectiveApiKey)
                    .baseUrl(baseUrl)
                    .build();

            EmbeddingModel embeddingModel = new OpenAiEmbeddingModel(
                    openAiApi,
                    MetadataMode.EMBED,
                    OpenAiEmbeddingOptions.builder()
                            .model(model)
                            .build()
            );

            logger.info("Created OpenAI EmbeddingModel with model: {}", model);
            return embeddingModel;
        } catch (Exception e) {
            logger.warn("Failed to create OpenAI EmbeddingModel: {}", e.getMessage());
            return null;
        }
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