package org.tanzu.mcpclient.model;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * ModelProvider implementation that provides models from traditional Spring AI properties.
 * This has lower priority than GenaiLocator but serves as a fallback for property-based configuration.
 */
@Component
public class PropertyBasedModelProvider implements ModelProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(PropertyBasedModelProvider.class);
    private static final int PRIORITY = 10; // Lower priority than GenaiLocator
    
    private final ModelDiscoveryService modelDiscoveryService;
    private final OpenAiApi chatOpenAiApi;
    private final OpenAiApi embeddingOpenAiApi;
    private final RetryTemplate retryTemplate;
    private final ObservationRegistry observationRegistry;
    private final ToolCallingManager toolCallingManager;
    
    public PropertyBasedModelProvider(ModelDiscoveryService modelDiscoveryService, 
                                    OpenAiApi chatOpenAiApi,
                                    OpenAiApi embeddingOpenAiApi,
                                    RetryTemplate retryTemplate,
                                    ObservationRegistry observationRegistry, 
                                    ToolCallingManager toolCallingManager) {
        this.modelDiscoveryService = modelDiscoveryService;
        this.chatOpenAiApi = chatOpenAiApi;
        this.embeddingOpenAiApi = embeddingOpenAiApi;
        this.retryTemplate = retryTemplate;
        this.observationRegistry = observationRegistry;
        this.toolCallingManager = toolCallingManager;
    }
    
    @Override
    public Optional<ChatModel> getChatModel() {
        GenaiModel config = modelDiscoveryService.getChatModelConfig();
        
        // Only provide model if it's from properties (not GenaiLocator)
        if (config.isFromGenaiLocator()) {
            logger.debug("Chat model config is from GenaiLocator - PropertyBasedModelProvider will not provide model");
            return Optional.empty();
        }
        
        try {
            ChatModel chatModel = createPropertyBasedChatModel(config);
            logger.debug("Successfully created property-based ChatModel with model='{}'", config.modelName());
            return Optional.of(chatModel);
        } catch (Exception e) {
            logger.warn("Failed to create property-based ChatModel: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<EmbeddingModel> getEmbeddingModel() {
        GenaiModel config = modelDiscoveryService.getEmbeddingModelConfig();
        
        // Only provide model if it's from properties (not GenaiLocator)
        if (config.isFromGenaiLocator()) {
            logger.debug("Embedding model config is from GenaiLocator - PropertyBasedModelProvider will not provide model");
            return Optional.empty();
        }
        
        try {
            EmbeddingModel embeddingModel = createPropertyBasedEmbeddingModel(config);
            logger.debug("Successfully created property-based EmbeddingModel with model='{}'", config.modelName());
            return Optional.of(embeddingModel);
        } catch (Exception e) {
            logger.warn("Failed to create property-based EmbeddingModel: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    @Override
    public int getPriority() {
        return PRIORITY;
    }
    
    @Override
    public String getProviderName() {
        return "PropertyBased";
    }
    
    /**
     * Creates a property-based ChatModel using traditional Spring AI configuration.
     */
    private ChatModel createPropertyBasedChatModel(GenaiModel config) {
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
        
        return new OpenAiChatModel(chatOpenAiApi, options, toolCallingManager, retryTemplate, observationRegistry);
    }
    
    /**
     * Creates a property-based EmbeddingModel using traditional Spring AI configuration.
     */
    private EmbeddingModel createPropertyBasedEmbeddingModel(GenaiModel config) {
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
        
        return new OpenAiEmbeddingModel(embeddingOpenAiApi, MetadataMode.EMBED, options, retryTemplate);
    }
}