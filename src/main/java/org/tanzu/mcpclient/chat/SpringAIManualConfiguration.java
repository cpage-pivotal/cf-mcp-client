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
 * Manual Spring AI configuration that supports mixed configurations:
 * - GenaiLocator for some models (new Tanzu Platform 10.2+ format)
 * - Property-based configuration for other models (traditional format)
 *
 * Each model type (chat, embedding) is handled independently, allowing for flexible
 * combinations of GenaiLocator and property-based models.
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
            logger.info("GenaiLocator available - will use for models when available, with property-based fallback");
        } else {
            logger.info("GenaiLocator not available - using property-based model configuration only");
        }
    }

    /**
     * Creates OpenAI API client for property-based models.
     * This is used when models need to be created from traditional property configuration.
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenAiApi openAiApi() {
        String apiKey = getApiKey();
        String baseUrl = getBaseUrl();

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
     * Creates ChatModel bean with independent model resolution.
     * Tries GenaiLocator first, then falls back to property-based configuration.
     */
    @Bean
    @ConditionalOnMissingBean
    public ChatModel chatModel(OpenAiApi openAiApi, RetryTemplate retryTemplate,
                               ObservationRegistry observationRegistry, ToolCallingManager toolCallingManager) {

        // Priority 1: Try GenaiLocator if available
        if (genaiLocator != null) {
            try {
                ChatModel locatorModel = genaiLocator.getFirstAvailableChatModel();
                logger.info("Using ChatModel from GenaiLocator: {}", locatorModel.getClass().getSimpleName());
                return locatorModel;
            } catch (Exception e) {
                logger.debug("No chat model available from GenaiLocator, falling back to property-based configuration: {}",
                        e.getMessage());
            }
        }

        // Priority 2: Use property-based configuration
        return createPropertyBasedChatModel(openAiApi, retryTemplate, observationRegistry, toolCallingManager);
    }

    /**
     * Creates EmbeddingModel bean with independent model resolution.
     * Tries GenaiLocator first, then falls back to property-based configuration.
     */
    @Bean
    @ConditionalOnMissingBean
    public EmbeddingModel embeddingModel(OpenAiApi openAiApi, RetryTemplate retryTemplate) {

        // Priority 1: Try GenaiLocator if available
        if (genaiLocator != null) {
            try {
                EmbeddingModel locatorModel = genaiLocator.getFirstAvailableEmbeddingModel();
                logger.info("Using EmbeddingModel from GenaiLocator: {}", locatorModel.getClass().getSimpleName());
                return locatorModel;
            } catch (Exception e) {
                logger.debug("No embedding model available from GenaiLocator, falling back to property-based configuration: {}",
                        e.getMessage());
            }
        }

        // Priority 2: Use property-based configuration
        return createPropertyBasedEmbeddingModel(openAiApi, retryTemplate);
    }

    /**
     * Creates a property-based ChatModel using traditional Spring AI configuration.
     */
    private ChatModel createPropertyBasedChatModel(OpenAiApi openAiApi, RetryTemplate retryTemplate,
                                                   ObservationRegistry observationRegistry, ToolCallingManager toolCallingManager) {
        String model = getChatModel();

        if (model == null || model.isEmpty()) {
            logger.warn("No chat model configured in properties - creating non-functional ChatModel bean");
            model = "no-model-configured";
        } else {
            logger.info("Creating property-based ChatModel with model='{}', apiKey exists={}",
                    model, hasValidApiKey());
        }

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.8)
                .build();

        return new OpenAiChatModel(openAiApi, options, toolCallingManager, retryTemplate, observationRegistry);
    }

    /**
     * Creates a property-based EmbeddingModel using traditional Spring AI configuration.
     */
    private EmbeddingModel createPropertyBasedEmbeddingModel(OpenAiApi openAiApi, RetryTemplate retryTemplate) {
        String model = getEmbeddingModel();

        if (model == null || model.isEmpty()) {
            logger.warn("No embedding model configured in properties - creating non-functional EmbeddingModel bean");
            model = "no-model-configured";
        } else {
            logger.info("Creating property-based EmbeddingModel with model='{}', apiKey exists={}",
                    model, hasValidApiKey());
        }

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
        logger.debug("Creating ChatClient.Builder with ChatModel: {}", chatModel.getClass().getSimpleName());
        return ChatClient.builder(chatModel);
    }

    // Helper methods that mirror GenAIService logic for property-based configuration
    private String getApiKey() {
        // Check in order of precedence for different model types
        String key = environment.getProperty("spring.ai.openai.chat.api-key");
        if (key != null && !key.isEmpty()) {
            logger.debug("Found chat-specific API key");
            return key;
        }

        key = environment.getProperty("spring.ai.openai.embedding.api-key");
        if (key != null && !key.isEmpty()) {
            logger.debug("Found embedding-specific API key");
            return key;
        }

        key = environment.getProperty("spring.ai.openai.api-key");
        if (key != null && !key.isEmpty()) {
            logger.debug("Found general OpenAI API key");
            return key;
        }

        logger.debug("No API key found in properties");
        return null;
    }

    private String getBaseUrl() {
        // Check in order of precedence for different model types
        String url = environment.getProperty("spring.ai.openai.chat.base-url");
        if (url != null && !url.isEmpty()) {
            logger.debug("Found chat-specific base URL: {}", url);
            return url;
        }

        url = environment.getProperty("spring.ai.openai.embedding.base-url");
        if (url != null && !url.isEmpty()) {
            logger.debug("Found embedding-specific base URL: {}", url);
            return url;
        }

        url = environment.getProperty("spring.ai.openai.base-url");
        if (url != null && !url.isEmpty()) {
            logger.debug("Found general OpenAI base URL: {}", url);
            return url;
        }

        logger.debug("No base URL found in properties, using default");
        return "https://api.openai.com";
    }

    private String getChatModel() {
        String model = environment.getProperty(GenAIService.CHAT_MODEL);
        logger.debug("Chat model from properties: '{}'", model);
        return Objects.requireNonNullElse(model, "");
    }

    private String getEmbeddingModel() {
        String model = environment.getProperty(GenAIService.EMBEDDING_MODEL);
        logger.debug("Embedding model from properties: '{}'", model);
        return Objects.requireNonNullElse(model, "");
    }

    private boolean hasValidApiKey() {
        String apiKey = getApiKey();
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("no-api-key-configured");
    }
}