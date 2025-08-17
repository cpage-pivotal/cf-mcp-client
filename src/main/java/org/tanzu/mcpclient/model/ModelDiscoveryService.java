package org.tanzu.mcpclient.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for detecting and getting information about GenAI services.
 * This service handles each model type (chat, embedding) independently to support
 * flexible deployment scenarios. Now supports multiple GenaiLocator instances
 * through MultiGenaiLocatorAggregator.
 */
@Service
public class ModelDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(ModelDiscoveryService.class);

    // Constants
    public static final String CHAT_MODEL = "spring.ai.openai.chat.options.model";
    public static final String EMBEDDING_MODEL = "spring.ai.openai.embedding.options.model";

    private final Environment environment;
    private final MultiGenaiLocatorAggregator aggregator;

    /**
     * Constructor with MultiGenaiLocatorAggregator injection.
     * The aggregator provides access to all available GenaiLocator instances.
     */
    public ModelDiscoveryService(Environment environment, MultiGenaiLocatorAggregator aggregator) {
        this.environment = environment;
        this.aggregator = aggregator;

        if (aggregator.hasAnyLocators()) {
            logger.debug("MultiGenaiLocatorAggregator detected with {} locators - will check for dynamic model discovery", 
                    aggregator.getLocatorCount());
        } else {
            logger.debug("No GenaiLocator instances available - using property-based configuration only");
        }
    }

    /**
     * Checks if an embedding model is available from any source.
     */
    public boolean isEmbeddingModelAvailable() {
        // Check aggregated GenaiLocators first
        if (isEmbeddingModelAvailableFromLocators()) {
            return true;
        }

        // Check property-based configuration
        String model = environment.getProperty(EMBEDDING_MODEL);
        return model != null && !model.isEmpty();
    }

    /**
     * Checks if a chat model is available from any source.
     */
    public boolean isChatModelAvailable() {
        // Check aggregated GenaiLocators first
        if (isChatModelAvailableFromLocators()) {
            return true;
        }

        // Check property-based configuration
        String model = environment.getProperty(CHAT_MODEL);
        return model != null && !model.isEmpty();
    }

    public String getEmbeddingModelName() {
        // Priority 1: Aggregated GenaiLocators (if available and have embedding models)
        if (aggregator.hasAnyLocators()) {
            try {
                List<String> embeddingModels = aggregator.aggregateModelNamesByCapability("EMBEDDING");
                if (embeddingModels != null && !embeddingModels.isEmpty()) {
                    String firstModel = embeddingModels.getFirst();
                    logger.debug("Using first available embedding model from aggregated GenaiLocators: {}", firstModel);
                    return firstModel;
                }
            } catch (Exception e) {
                logger.debug("No embedding model available from aggregated GenaiLocators: {}", e.getMessage());
            }
        }

        // Priority 2: Property-based configuration
        String model = environment.getProperty(EMBEDDING_MODEL);
        if (model != null && !model.isEmpty()) {
            logger.debug("Using embedding model from properties: {}", model);
            return model;
        }

        logger.debug("No embedding model configured from any source, returning empty");
        return "";
    }

    public String getChatModelName() {
        // Priority 1: Aggregated GenaiLocators (if available and have chat models)
        if (aggregator.hasAnyLocators()) {
            try {
                List<String> chatModels = aggregator.aggregateModelNamesByCapability("CHAT");
                if (chatModels != null && !chatModels.isEmpty()) {
                    String firstModel = chatModels.getFirst();
                    logger.debug("Using first available chat model from aggregated GenaiLocators: {}", firstModel);
                    return firstModel;
                }
            } catch (Exception e) {
                logger.debug("No chat model available from aggregated GenaiLocators: {}", e.getMessage());
            }
        }

        // Priority 2: Property-based configuration
        String model = environment.getProperty(CHAT_MODEL);
        if (model != null && !model.isEmpty()) {
            logger.debug("Using chat model from properties: {}", model);
            return model;
        }

        logger.debug("No chat model configured from any source, returning empty");
        return "";
    }

    /**
     * Checks if a chat model is explicitly configured via properties.
     * Note: This only checks properties, not GenaiLocator.
     */
    public boolean isChatModelExplicitlyConfigured() {
        String model = environment.getProperty(CHAT_MODEL);
        return model != null && !model.isEmpty();
    }

    /**
     * Checks if an embedding model is explicitly configured via properties.
     * Note: This only checks properties, not GenaiLocator.
     */
    public boolean isEmbeddingModelExplicitlyConfigured() {
        String model = environment.getProperty(EMBEDDING_MODEL);
        return model != null && !model.isEmpty();
    }

    /**
     * Checks if chat models are available from aggregated GenaiLocators.
     * Returns false if no GenaiLocators are available or if an error occurs.
     */
    public boolean isChatModelAvailableFromLocators() {
        if (!aggregator.hasAnyLocators()) {
            return false;
        }
        try {
            List<String> chatModels = aggregator.aggregateModelNamesByCapability("CHAT");
            return chatModels != null && !chatModels.isEmpty();
        } catch (Exception e) {
            logger.debug("Error checking chat model availability from aggregated GenaiLocators: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if embedding models are available from aggregated GenaiLocators.
     * Returns false if no GenaiLocators are available or if an error occurs.
     */
    public boolean isEmbeddingModelAvailableFromLocators() {
        if (!aggregator.hasAnyLocators()) {
            return false;
        }
        try {
            List<String> embeddingModels = aggregator.aggregateModelNamesByCapability("EMBEDDING");
            return embeddingModels != null && !embeddingModels.isEmpty();
        } catch (Exception e) {
            logger.debug("Error checking embedding model availability from aggregated GenaiLocators: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gets all available chat model names from aggregated GenaiLocators.
     * @return List of chat model names from all locators
     */
    public List<String> getAllChatModelNames() {
        try {
            return aggregator.aggregateModelNamesByCapability("CHAT");
        } catch (Exception e) {
            logger.warn("Error getting all chat model names from aggregated GenaiLocators: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Gets all available embedding model names from aggregated GenaiLocators.
     * @return List of embedding model names from all locators
     */
    public List<String> getAllEmbeddingModelNames() {
        try {
            return aggregator.aggregateModelNamesByCapability("EMBEDDING");
        } catch (Exception e) {
            logger.warn("Error getting all embedding model names from aggregated GenaiLocators: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Gets the number of available GenaiLocator instances.
     * @return number of GenaiLocator instances
     */
    public int getLocatorCount() {
        return aggregator.getLocatorCount();
    }


    /**
     * Checks if any OpenAI API key is configured for property-based models.
     * Uses the same priority logic as SpringAIConfiguration.
     */
    public boolean hasApiKey() {
        String key = getApiKey();
        return key != null && !key.isEmpty();
    }

    /**
     * Gets the complete chat model configuration including API key, base URL, and source.
     * This consolidates all chat model resolution logic in one place.
     */
    public GenaiModel getChatModelConfig() {
        // Priority 1: Aggregated GenaiLocators (if available and have chat models)
        if (isChatModelAvailableFromLocators()) {
            String modelName = getChatModelName(); // This already handles aggregated GenaiLocator priority
            return new GenaiModel(
                modelName,
                "managed-by-genai-locator", // GenaiLocator handles auth
                "managed-by-genai-locator", // GenaiLocator handles base URL
                ModelSource.GENAI_LOCATOR
            );
        }

        // Priority 2: Property-based configuration
        String modelName = environment.getProperty(CHAT_MODEL);
        String apiKey = getApiKey();
        String baseUrl = getBaseUrl();

        if (modelName == null || modelName.isEmpty()) {
            logger.debug("No chat model configured from any source, returning default config");
            return GenaiModel.createDefault(ModelSource.PROPERTIES);
        }

        return new GenaiModel(modelName, apiKey, baseUrl, ModelSource.PROPERTIES);
    }

    /**
     * Gets the complete embedding model configuration including API key, base URL, and source.
     * This consolidates all embedding model resolution logic in one place.
     */
    public GenaiModel getEmbeddingModelConfig() {
        // Priority 1: Aggregated GenaiLocators (if available and have embedding models)
        if (isEmbeddingModelAvailableFromLocators()) {
            String modelName = getEmbeddingModelName(); // This already handles aggregated GenaiLocator priority
            return new GenaiModel(
                modelName,
                "managed-by-genai-locator", // GenaiLocator handles auth
                "managed-by-genai-locator", // GenaiLocator handles base URL
                ModelSource.GENAI_LOCATOR
            );
        }

        // Priority 2: Property-based configuration
        String modelName = environment.getProperty(EMBEDDING_MODEL);
        String apiKey = getApiKey();
        String baseUrl = getBaseUrl();

        if (modelName == null || modelName.isEmpty()) {
            logger.debug("No embedding model configured from any source, returning default config");
            return GenaiModel.createDefault(ModelSource.PROPERTIES);
        }

        return new GenaiModel(modelName, apiKey, baseUrl, ModelSource.PROPERTIES);
    }

    /**
     * Gets API key from properties with same priority logic as SpringAIConfiguration.
     */
    private String getApiKey() {
        // Check in order of precedence for different model types
        String key = environment.getProperty("spring.ai.openai.chat.api-key");
        if (key != null && !key.isEmpty()) {
            return key;
        }

        key = environment.getProperty("spring.ai.openai.embedding.api-key");
        if (key != null && !key.isEmpty()) {
            return key;
        }

        key = environment.getProperty("spring.ai.openai.api-key");
        if (key != null && !key.isEmpty()) {
            return key;
        }

        return null;
    }

    /**
     * Gets base URL from properties with same priority logic as SpringAIConfiguration.
     */
    private String getBaseUrl() {
        // Check in order of precedence for different model types
        String url = environment.getProperty("spring.ai.openai.chat.base-url");
        if (url != null && !url.isEmpty()) {
            return url;
        }

        url = environment.getProperty("spring.ai.openai.embedding.base-url");
        if (url != null && !url.isEmpty()) {
            return url;
        }

        url = environment.getProperty("spring.ai.openai.base-url");
        if (url != null && !url.isEmpty()) {
            return url;
        }

        return "https://api.openai.com";
    }

}