package org.tanzu.mcpclient.util;

import io.pivotal.cfenv.boot.genai.GenaiLocator;
import io.pivotal.cfenv.core.CfCredentials;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for detecting and getting information about GenAI services.
 * This service handles each model type (chat, embedding) independently to support
 * flexible deployment scenarios.
 */
@Service
public class GenAIService {
    private static final Logger logger = LoggerFactory.getLogger(GenAIService.class);

    // Constants
    public static final String MCP_SERVICE_URL = "mcpServiceURL";
    public static final String CHAT_MODEL = "spring.ai.openai.chat.options.model";
    public static final String EMBEDDING_MODEL = "spring.ai.openai.embedding.options.model";

    private final Environment environment;
    private final CfEnv cfEnv;
    private final GenaiLocator genaiLocator; // Optional - may be null

    /**
     * Constructor with optional GenaiLocator injection.
     * GenaiLocator is only available when GenaiLocatorAutoConfiguration is active
     * (i.e., when genai.locator.config-url property is set by CfGenaiProcessor).
     */
    public GenAIService(Environment environment, @Nullable GenaiLocator genaiLocator) {
        this.environment = environment;
        this.cfEnv = new CfEnv();
        this.genaiLocator = genaiLocator;

        if (genaiLocator != null) {
            logger.debug("GenaiLocator bean detected - will check for dynamic model discovery");
        } else {
            logger.debug("No GenaiLocator bean available - using property-based configuration only");
        }
    }

    /**
     * Checks if an embedding model is available from any source.
     */
    public boolean isEmbeddingModelAvailable() {
        // Check GenaiLocator first
        if (isEmbeddingModelAvailableFromLocator()) {
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
        // Check GenaiLocator first
        if (isChatModelAvailableFromLocator()) {
            return true;
        }

        // Check property-based configuration
        String model = environment.getProperty(CHAT_MODEL);
        return model != null && !model.isEmpty();
    }

    public String getEmbeddingModelName() {
        // Priority 1: GenaiLocator (if available and has embedding models)
        if (genaiLocator != null) {
            try {
                List<String> embeddingModels = genaiLocator.getModelNamesByCapability("EMBEDDING");
                if (embeddingModels != null && !embeddingModels.isEmpty()) {
                    String firstModel = embeddingModels.getFirst();
                    logger.debug("Using first available embedding model from GenaiLocator: {}", firstModel);
                    return firstModel;
                }
            } catch (Exception e) {
                logger.debug("No embedding model available from GenaiLocator: {}", e.getMessage());
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
        // Priority 1: GenaiLocator (if available and has chat models)
        if (genaiLocator != null) {
            try {
                List<String> chatModels = genaiLocator.getModelNamesByCapability("CHAT");
                if (chatModels != null && !chatModels.isEmpty()) {
                    String firstModel = chatModels.getFirst();
                    logger.debug("Using first available chat model from GenaiLocator: {}", firstModel);
                    return firstModel;
                }
            } catch (Exception e) {
                logger.debug("No chat model available from GenaiLocator: {}", e.getMessage());
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
     * Checks if chat models are available from GenaiLocator.
     * Returns false if GenaiLocator is not available or if an error occurs.
     */
    public boolean isChatModelAvailableFromLocator() {
        if (genaiLocator == null) {
            return false;
        }
        try {
            List<String> chatModels = genaiLocator.getModelNamesByCapability("CHAT");
            return chatModels != null && !chatModels.isEmpty();
        } catch (Exception e) {
            logger.debug("Error checking chat model availability from GenaiLocator: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if embedding models are available from GenaiLocator.
     * Returns false if GenaiLocator is not available or if an error occurs.
     */
    public boolean isEmbeddingModelAvailableFromLocator() {
        if (genaiLocator == null) {
            return false;
        }
        try {
            List<String> embeddingModels = genaiLocator.getModelNamesByCapability("EMBEDDING");
            return embeddingModels != null && !embeddingModels.isEmpty();
        } catch (Exception e) {
            logger.debug("Error checking embedding model availability from GenaiLocator: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gets MCP server URLs from GenaiLocator if available, otherwise from CF services.
     * This maintains backward compatibility while supporting the new approach.
     */
    public List<String> getMcpServiceUrlsFromLocator() {
        if (genaiLocator == null) {
            return List.of();
        }
        try {
            return genaiLocator.getMcpServers().stream()
                    .map(GenaiLocator.McpConnectivity::url)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.debug("Error getting MCP service URLs from GenaiLocator: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Checks if any OpenAI API key is configured for property-based models.
     * Uses the same priority logic as SpringAIManualConfiguration.
     */
    public boolean hasApiKey() {
        String key = environment.getProperty("spring.ai.openai.chat.api-key");
        if (key != null && !key.isEmpty()) {
            return true;
        }

        key = environment.getProperty("spring.ai.openai.embedding.api-key");
        if (key != null && !key.isEmpty()) {
            return true;
        }

        key = environment.getProperty("spring.ai.openai.api-key");
        return key != null && !key.isEmpty();
    }

    /**
     * Gets the names of MCP services from Cloud Foundry service bindings.
     */
    public List<String> getMcpServiceNames() {
        try {
            return cfEnv.findAllServices().stream()
                    .filter(this::hasMcpServiceUrl)
                    .map(CfService::getName)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Error getting MCP service names: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Gets the URLs of MCP services from Cloud Foundry service bindings.
     * This is the legacy approach - new approach uses GenaiLocator.
     */
    public List<String> getMcpServiceUrls() {
        try {
            return cfEnv.findAllServices().stream()
                    .filter(this::hasMcpServiceUrl)
                    .map(service -> service.getCredentials().getString(MCP_SERVICE_URL))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Error getting MCP service URLs: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Gets all MCP service URLs from both sources (GenaiLocator and CF services).
     * This ensures compatibility with both old and new approaches.
     */
    public List<String> getAllMcpServiceUrls() {
        List<String> locatorUrls = getMcpServiceUrlsFromLocator();
        List<String> cfUrls = getMcpServiceUrls();

        // Combine both sources, preferring GenaiLocator if available
        if (!locatorUrls.isEmpty()) {
            logger.debug("Using MCP service URLs from GenaiLocator: {}", locatorUrls);
            return locatorUrls;
        } else if (!cfUrls.isEmpty()) {
            logger.debug("Using MCP service URLs from CF services: {}", cfUrls);
            return cfUrls;
        } else {
            logger.debug("No MCP service URLs found from any source");
            return List.of();
        }
    }

    /**
     * Checks if a Cloud Foundry service has an MCP service URL configured.
     */
    public boolean hasMcpServiceUrl(CfService service) {
        CfCredentials credentials = service.getCredentials();
        return credentials != null && credentials.getString(MCP_SERVICE_URL) != null;
    }
}