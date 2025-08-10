package org.tanzu.mcpclient.chat;

import io.micrometer.observation.ObservationRegistry;
import io.pivotal.cfenv.boot.genai.GenaiLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.retry.support.RetryTemplate;
import org.tanzu.mcpclient.util.GenAIService;

import java.util.Objects;

/**
 * Manual Spring AI configuration that supports both traditional property-based configuration
 * and the new GenaiLocator-based configuration for Tanzu Platform 10.2+.
 *
 * When GenaiLocator is available, it takes precedence and provides properly configured models.
 * Falls back to property-based configuration when GenaiLocator is not available.
 */
@Configuration
public class SpringAIManualConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SpringAIManualConfiguration.class);

    private final Environment environment;
    private final GenaiLocator genaiLocator; // Optional - may be null

    public SpringAIManualConfiguration(Environment environment, @Nullable GenaiLocator genaiLocator) {
        this.environment = environment;
        this.genaiLocator = genaiLocator;

        if (genaiLocator != null) {
            logger.info("GenaiLocator available - will use locator-provided models");
        } else {
            logger.info("GenaiLocator not available - using property-based model configuration");
        }
    }

    /**
     * Creates OpenAI API client only when using property-based configuration.
     * When GenaiLocator is available, models are created by the locator itself.
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenAiApi openAiApi() {
        // If GenaiLocator is available, we don't need a centralized OpenAiApi
        // because the locator creates its own APIs for each model
        if (genaiLocator != null) {
            logger.debug("GenaiLocator available - creating minimal OpenAiApi bean for compatibility");
            // Create a minimal API client for compatibility, but it won't be used
            return OpenAiApi.builder()
                    .apiKey("not-used-with-genai-locator")
                    .baseUrl("https://api.openai.com") // Default URL
                    .build();
        }

        String apiKey = getApiKey();
        String baseUrl = getBaseUrl();

        logger.debug("Creating OpenAiApi with baseUrl={}, hasApiKey={}",
                baseUrl, apiKey != null && !apiKey.isEmpty());

        // Only create functional API client if we have proper credentials
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("placeholder")) {
            logger.warn("No valid API key found for OpenAI models - models will be non-functional");
            apiKey = "no-api-key-configured"; // Avoid the "placeholder" that triggers specific OpenAI error
        }

        return OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Creates RetryTemplate bean if not already available
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryTemplate retryTemplate() {
        return RetryUtils.DEFAULT_RETRY_TEMPLATE;
    }

    /**
     * Creates ObservationRegistry bean if not already available
     */
    @Bean
    @ConditionalOnMissingBean
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.NOOP;
    }

    /**
     * Creates ToolCallingManager bean if not already available
     */
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
     * Creates ChatModel bean with proper priority handling.
     * Uses GenaiLocator if available, otherwise falls back to property-based configuration.
     */
    @Bean
    @ConditionalOnMissingBean
    public ChatModel chatModel(OpenAiApi openAiApi, RetryTemplate retryTemplate,
                               ObservationRegistry observationRegistry, ToolCallingManager toolCallingManager) {

        // Priority 1: Use GenaiLocator if available and has chat models
        if (genaiLocator != null) {
            try {
                ChatModel locatorModel = genaiLocator.getFirstAvailableChatModel();
                logger.info("Using ChatModel from GenaiLocator");
                return locatorModel;
            } catch (Exception e) {
                logger.warn("Failed to get ChatModel from GenaiLocator, falling back to property-based configuration: {}",
                        e.getMessage());
            }
        }

        // Priority 2: Use property-based configuration
        String model = getChatModel();

        if (model == null || model.isEmpty()) {
            logger.warn("No chat model configured - creating non-functional ChatModel bean");
            model = "no-model-configured";
        }

        logger.debug("Creating property-based ChatModel with model='{}'", model);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.8)
                .build();

        return new OpenAiChatModel(openAiApi, options, toolCallingManager, retryTemplate, observationRegistry);
    }

    /**
     * Creates EmbeddingModel bean with proper priority handling.
     * Uses GenaiLocator if available, otherwise falls back to property-based configuration.
     */
    @Bean
    @ConditionalOnMissingBean
    public EmbeddingModel embeddingModel(OpenAiApi openAiApi, RetryTemplate retryTemplate) {

        // Priority 1: Use GenaiLocator if available and has embedding models
        if (genaiLocator != null) {
            try {
                EmbeddingModel locatorModel = genaiLocator.getFirstAvailableEmbeddingModel();
                logger.info("Using EmbeddingModel from GenaiLocator");
                return locatorModel;
            } catch (Exception e) {
                logger.warn("Failed to get EmbeddingModel from GenaiLocator, falling back to property-based configuration: {}",
                        e.getMessage());
            }
        }

        // Priority 2: Use property-based configuration
        String model = getEmbeddingModel();

        if (model == null || model.isEmpty()) {
            logger.warn("No embedding model configured - creating non-functional EmbeddingModel bean");
            model = "no-model-configured";
        }

        logger.debug("Creating property-based EmbeddingModel with model='{}'", model);

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(model)
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options, retryTemplate);
    }

    /**
     * Creates ChatClient.Builder bean. This must always be available for dependency injection.
     */
    @Bean
    @ConditionalOnMissingBean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        logger.debug("Creating ChatClient.Builder");
        return ChatClient.builder(chatModel);
    }

    // Helper methods that mirror GenAIService logic
    private String getApiKey() {
        String key = environment.getProperty("spring.ai.openai.chat.api-key");
        if (key == null || key.isEmpty()) {
            key = environment.getProperty("spring.ai.openai.embedding.api-key");
        }
        if (key == null || key.isEmpty()) {
            key = environment.getProperty("spring.ai.openai.api-key");
        }
        return key;
    }

    private String getBaseUrl() {
        String url = environment.getProperty("spring.ai.openai.chat.base-url");
        if (url == null || url.isEmpty()) {
            url = environment.getProperty("spring.ai.openai.embedding.base-url");
        }
        if (url == null || url.isEmpty()) {
            url = environment.getProperty("spring.ai.openai.base-url", "https://api.openai.com");
        }
        return url;
    }

    private String getChatModel() {
        return Objects.requireNonNullElse(
                environment.getProperty(GenAIService.CHAT_MODEL), "");
    }

    private String getEmbeddingModel() {
        return Objects.requireNonNullElse(
                environment.getProperty(GenAIService.EMBEDDING_MODEL), "");
    }
}