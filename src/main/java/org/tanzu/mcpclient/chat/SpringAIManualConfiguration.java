package org.tanzu.mcpclient.chat;

import io.micrometer.observation.ObservationRegistry;
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
import org.springframework.retry.support.RetryTemplate;
import org.tanzu.mcpclient.util.GenAIService;

import java.util.Objects;

/**
 * Manual Spring AI configuration that preserves graceful degradation principles.
 * This replaces the auto-configuration while maintaining the same behavior:
 * - Application starts successfully without models
 * - Beans are created but may be non-functional
 * - Validation occurs at service level, not configuration level
 */
@Configuration
public class SpringAIManualConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SpringAIManualConfiguration.class);

    private final Environment environment;

    public SpringAIManualConfiguration(Environment environment) {
        this.environment = environment;
    }

    /**
     * Creates OpenAI API client. Returns a functional client if API key is available,
     * or a minimal client that will fail gracefully at runtime if not.
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenAiApi openAiApi() {
        String apiKey = getApiKey();
        String baseUrl = getBaseUrl();

        logger.debug("Creating OpenAiApi with baseUrl={}, hasApiKey={}",
                baseUrl, apiKey != null && !apiKey.isEmpty());

        // Always create the API client - graceful degradation happens at service level
        return OpenAiApi.builder()
                .apiKey(apiKey != null ? apiKey : "placeholder") // Avoid null
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
     * Creates ChatModel bean using the correct Spring AI 1.0.1 constructor.
     * Always creates the bean but it may be non-functional if no model or API key is configured.
     * This preserves graceful degradation.
     */
    @Bean
    @ConditionalOnMissingBean
    public ChatModel chatModel(OpenAiApi openAiApi, RetryTemplate retryTemplate,
                               ObservationRegistry observationRegistry, ToolCallingManager toolCallingManager) {
        String model = getChatModel();

        logger.debug("Creating ChatModel with model={}", model);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model.isEmpty() ? "gpt-4o-mini" : model)
                .temperature(0.8)
                .build();

        // Use the correct constructor signature for Spring AI 1.0.1
        return new OpenAiChatModel(openAiApi, options, toolCallingManager, retryTemplate, observationRegistry);
    }

    /**
     * Creates EmbeddingModel bean. Always creates the bean but it may be non-functional
     * if no model or API key is configured. This preserves graceful degradation.
     */
    @Bean
    @ConditionalOnMissingBean
    public EmbeddingModel embeddingModel(OpenAiApi openAiApi, RetryTemplate retryTemplate) {
        String model = getEmbeddingModel();

        logger.debug("Creating EmbeddingModel with model={}", model);

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(model.isEmpty() ? "text-embedding-3-small" : model)
                .build();

        // Use the correct constructor signature for Spring AI 1.0.1
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
        // Check in order: specific chat key, specific embedding key, general key
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
        // Check in order: specific chat URL, specific embedding URL, general URL
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