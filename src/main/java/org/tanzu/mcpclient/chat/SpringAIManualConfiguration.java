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
import org.tanzu.mcpclient.util.ModelConfig;
import org.tanzu.mcpclient.util.ModelSource;

/**
 * Manual Spring AI configuration that supports mixed configurations:
 * - GenaiLocator for some models (new Tanzu Platform 10.2+ format)
 * - Property-based configuration for other models (traditional format)
 */
@Configuration
public class SpringAIManualConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SpringAIManualConfiguration.class);

    private final Environment environment;
    private final GenaiLocator genaiLocator; // Optional - may be null
    private final GenAIService genAIService;

    public SpringAIManualConfiguration(Environment environment, @Nullable GenaiLocator genaiLocator, GenAIService genAIService) {
        this.environment = environment;
        this.genaiLocator = genaiLocator;
        this.genAIService = genAIService;

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
        // Use a representative model config (chat) for API client setup since both models share the same API
        ModelConfig config = genAIService.getChatModelConfig();
        
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
     * Creates ChatModel bean using consolidated GenAIService configuration.
     */
    @Bean
    @ConditionalOnMissingBean
    public ChatModel chatModel(OpenAiApi openAiApi, RetryTemplate retryTemplate,
                               ObservationRegistry observationRegistry, ToolCallingManager toolCallingManager) {

        ModelConfig config = genAIService.getChatModelConfig();

        // Priority 1: Use GenaiLocator model if available
        if (config.isFromGenaiLocator() && genaiLocator != null) {
            try {
                ChatModel locatorModel = genaiLocator.getFirstAvailableChatModel();
                logger.info("Using ChatModel from GenaiLocator: {}", locatorModel.getClass().getSimpleName());
                return locatorModel;
            } catch (Exception e) {
                logger.debug("No chat model available from GenaiLocator, falling back to property-based configuration: {}",
                        e.getMessage());
                // Fall through to property-based creation with default config
                config = ModelConfig.createDefault(ModelSource.PROPERTIES);
            }
        }

        // Priority 2: Use property-based configuration
        return createPropertyBasedChatModel(config, openAiApi, retryTemplate, observationRegistry, toolCallingManager);
    }

    /**
     * Creates EmbeddingModel bean using consolidated GenAIService configuration.
     */
    @Bean
    @ConditionalOnMissingBean
    public EmbeddingModel embeddingModel(OpenAiApi openAiApi, RetryTemplate retryTemplate) {

        ModelConfig config = genAIService.getEmbeddingModelConfig();

        // Priority 1: Use GenaiLocator model if available
        if (config.isFromGenaiLocator() && genaiLocator != null) {
            try {
                EmbeddingModel locatorModel = genaiLocator.getFirstAvailableEmbeddingModel();
                logger.info("Using EmbeddingModel from GenaiLocator: {}", locatorModel.getClass().getSimpleName());
                return locatorModel;
            } catch (Exception e) {
                logger.debug("No embedding model available from GenaiLocator, falling back to property-based configuration: {}",
                        e.getMessage());
                // Fall through to property-based creation with default config
                config = ModelConfig.createDefault(ModelSource.PROPERTIES);
            }
        }

        // Priority 2: Use property-based configuration
        return createPropertyBasedEmbeddingModel(config, openAiApi, retryTemplate);
    }

    /**
     * Creates a property-based ChatModel using traditional Spring AI configuration.
     */
    private ChatModel createPropertyBasedChatModel(ModelConfig config, OpenAiApi openAiApi, RetryTemplate retryTemplate,
                                                   ObservationRegistry observationRegistry, ToolCallingManager toolCallingManager) {
        String model = config.modelName();

        if (model == null || model.isEmpty()) {
            logger.warn("No chat model configured in properties - creating non-functional ChatModel bean");
            model = "no-model-configured";
        } else {
            logger.info("Creating property-based ChatModel with model='{}', apiKey exists={}",
                    model, config.isValid());
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
    private EmbeddingModel createPropertyBasedEmbeddingModel(ModelConfig config, OpenAiApi openAiApi, RetryTemplate retryTemplate) {
        String model = config.modelName();

        if (model == null || model.isEmpty()) {
            logger.warn("No embedding model configured in properties - creating non-functional EmbeddingModel bean");
            model = "no-model-configured";
        } else {
            logger.info("Creating property-based EmbeddingModel with model='{}', apiKey exists={}",
                    model, config.isValid());
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

}