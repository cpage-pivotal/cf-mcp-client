package org.tanzu.mcpclient.util;

import io.pivotal.cfenv.boot.genai.GenaiLocator;
import io.pivotal.cfenv.core.CfCredentials;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for detecting and getting information about GenAI services.
 * Updated to use GenAI Locator when available, with fallback to property-based detection.
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
    private final GenaiLocator genaiLocator;

    public GenAIService(Environment environment,
                        @Autowired(required = false) GenaiLocator genaiLocator) {
        this.environment = environment;
        this.cfEnv = new CfEnv();
        this.genaiLocator = genaiLocator;

        logger.info("GenAIService initialized with GenAI Locator: {}",
                genaiLocator != null ? "Available" : "Not Available");
    }

    /**
     * Check if embedding model is available via GenAI Locator or fallback to properties
     */
    public boolean isEmbeddingModelAvailable() {
        // Try GenAI Locator first
        if (genaiLocator != null) {
            try {
                genaiLocator.getFirstAvailableEmbeddingModel();
                logger.debug("Embedding model available via GenAI Locator");
                return true;
            } catch (Exception e) {
                logger.debug("No embedding model available via GenAI Locator: {}", e.getMessage());
            }
        }

        // Fallback to property-based detection
        boolean propertyAvailable = environment.getProperty(EMBEDDING_MODEL) != null;
        logger.debug("Embedding model available via properties: {}", propertyAvailable);
        return propertyAvailable;
    }

    /**
     * Get embedding model name via GenAI Locator or fallback to properties
     */
    public String getEmbeddingModelName() {
        // Try GenAI Locator first
        if (genaiLocator != null) {
            try {
                List<String> embeddingModels = genaiLocator.getModelNamesByCapability("EMBEDDING");
                if (!embeddingModels.isEmpty()) {
                    String modelName = embeddingModels.get(0);
                    logger.debug("Embedding model name from GenAI Locator: {}", modelName);
                    return modelName;
                }
            } catch (Exception e) {
                logger.debug("Error getting embedding model name from GenAI Locator: {}", e.getMessage());
            }
        }

        // Fallback to property-based
        String propertyModel = Objects.requireNonNullElse(environment.getProperty(EMBEDDING_MODEL), "");
        logger.debug("Embedding model name from properties: {}", propertyModel);
        return propertyModel;
    }

    /**
     * Get chat model name via GenAI Locator or fallback to properties
     */
    public String getChatModelName() {
        // Try GenAI Locator first
        if (genaiLocator != null) {
            try {
                List<String> chatModels = genaiLocator.getModelNamesByCapability("CHAT");
                if (!chatModels.isEmpty()) {
                    String modelName = chatModels.get(0);
                    logger.debug("Chat model name from GenAI Locator: {}", modelName);
                    return modelName;
                }
            } catch (Exception e) {
                logger.debug("Error getting chat model name from GenAI Locator: {}", e.getMessage());
            }
        }

        // Fallback to property-based (only if API key is present)
        String apiKey = environment.getProperty("spring.ai.openai.api-key");
        String chatApiKey = environment.getProperty("spring.ai.openai.chat.api-key");

        if ((apiKey != null && !apiKey.trim().isEmpty()) || (chatApiKey != null && !chatApiKey.trim().isEmpty())) {
            String propertyModel = Objects.requireNonNullElse(environment.getProperty(CHAT_MODEL), "");
            if (!propertyModel.isEmpty()) {
                logger.debug("Chat model name from properties: {}", propertyModel);
                return propertyModel;
            }
        }

        logger.debug("No chat model available");
        return "";
    }

    /**
     * Get all available model names by capability (only works with GenAI Locator)
     */
    public List<String> getModelNamesByCapability(String capability) {
        if (genaiLocator != null) {
            try {
                return genaiLocator.getModelNamesByCapability(capability);
            } catch (Exception e) {
                logger.debug("Error getting models by capability '{}': {}", capability, e.getMessage());
            }
        }
        return List.of();
    }

    /**
     * Get all available chat models (only works with GenAI Locator)
     */
    public List<String> getAvailableChatModels() {
        return getModelNamesByCapability("CHAT");
    }

    /**
     * Get all available embedding models (only works with GenAI Locator)
     */
    public List<String> getAvailableEmbeddingModels() {
        return getModelNamesByCapability("EMBEDDING");
    }

    /**
     * Get all available tool models (only works with GenAI Locator)
     */
    public List<String> getAvailableToolModels() {
        return getModelNamesByCapability("TOOLS");
    }

    /**
     * Check if GenAI Locator is available and functional
     */
    public boolean isGenaiLocatorAvailable() {
        if (genaiLocator == null) {
            return false;
        }

        try {
            // Test if we can call the locator without error
            genaiLocator.getModelNames();
            return true;
        } catch (Exception e) {
            logger.debug("GenAI Locator not functional: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get the actual EmbeddingModel instance (only works with GenAI Locator)
     */
    public EmbeddingModel getEmbeddingModel() {
        if (genaiLocator != null) {
            try {
                return genaiLocator.getFirstAvailableEmbeddingModel();
            } catch (Exception e) {
                logger.debug("Error getting embedding model instance: {}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * Get MCP service names - unchanged from original implementation
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
     * Get MCP service URLs - unchanged from original implementation
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
     * Check if service has MCP URL - unchanged from original implementation
     */
    public boolean hasMcpServiceUrl(CfService service) {
        CfCredentials credentials = service.getCredentials();
        return credentials != null && credentials.getString(MCP_SERVICE_URL) != null;
    }

    /**
     * Get MCP service URLs from GenAI Locator (if supported)
     */
    public List<String> getMcpServiceUrlsFromLocator() {
        if (genaiLocator != null) {
            try {
                return genaiLocator.getMcpServers().stream()
                        .map(GenaiLocator.McpConnectivity::url)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                logger.debug("Error getting MCP servers from GenAI Locator: {}", e.getMessage());
            }
        }
        return List.of();
    }

    /**
     * Get all MCP service URLs from both sources
     */
    public List<String> getAllMcpServiceUrls() {
        List<String> cfEnvUrls = getMcpServiceUrls();
        List<String> locatorUrls = getMcpServiceUrlsFromLocator();

        return Stream.concat(cfEnvUrls.stream(), locatorUrls.stream())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Get diagnostics information for debugging
     */
    public GenAIDiagnostics getDiagnostics() {
        return new GenAIDiagnostics(
                isGenaiLocatorAvailable(),
                getChatModelName(),
                getEmbeddingModelName(),
                getAvailableChatModels(),
                getAvailableEmbeddingModels(),
                getMcpServiceNames(),
                getMcpServiceUrls(),
                getMcpServiceUrlsFromLocator()
        );
    }

    /**
     * Diagnostics record for debugging and monitoring
     */
    public record GenAIDiagnostics(
            boolean genaiLocatorAvailable,
            String currentChatModel,
            String currentEmbeddingModel,
            List<String> availableChatModels,
            List<String> availableEmbeddingModels,
            List<String> mcpServiceNames,
            List<String> mcpServiceUrls,
            List<String> mcpServiceUrlsFromLocator
    ) {}
}