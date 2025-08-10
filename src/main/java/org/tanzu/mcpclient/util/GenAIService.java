package org.tanzu.mcpclient.util;

import io.pivotal.cfenv.core.CfCredentials;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for detecting and getting information about GenAI services.
 * Updated to remove default model application - empty configuration means no models.
 */
@Service
public class GenAIService {
    private static final Logger logger = LoggerFactory.getLogger(GenAIService.class);

    // Constants
    public static final String MCP_SERVICE_URL = "mcpServiceURL";

    public static final String CHAT_MODEL = "spring.ai.openai.chat.options.model";
    public static final String EMBEDDING_MODEL = "spring.ai.openai.embedding.options.model";

    // REMOVED: Default model constants - no longer applying defaults
    // private static final String DEFAULT_CHAT_MODEL = "gpt-4o-mini";
    // private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";

    private final Environment environment;
    private final CfEnv cfEnv;

    public GenAIService(Environment environment) {
        this.environment = environment;
        this.cfEnv = new CfEnv();
    }

    public boolean isEmbeddingModelAvailable() {
        String model = environment.getProperty(EMBEDDING_MODEL);
        return model != null && !model.isEmpty();
    }

    /**
     * Gets the embedding model name.
     * CHANGE: Returns empty string if not explicitly configured - no defaults applied.
     */
    public String getEmbeddingModelName() {
        String model = environment.getProperty(EMBEDDING_MODEL);

        // If explicitly configured, return it
        if (model != null && !model.isEmpty()) {
            return model;
        }

        // REMOVED: Default application logic
        // OLD: If not configured but we have an API key, apply default
        // NEW: Always return empty if not explicitly configured
        logger.debug("No embedding model explicitly configured, returning empty");
        return "";
    }

    /**
     * Gets the chat model name.
     * CHANGE: Returns empty string if not explicitly configured - no defaults applied.
     */
    public String getChatModelName() {
        String model = environment.getProperty(CHAT_MODEL);

        // If explicitly configured, return it
        if (model != null && !model.isEmpty()) {
            return model;
        }

        // REMOVED: Default application logic
        // OLD: If not configured but we have an API key, apply default
        // NEW: Always return empty if not explicitly configured
        logger.debug("No chat model explicitly configured, returning empty");
        return "";
    }

    /**
     * Checks if a chat model is explicitly configured via properties.
     */
    public boolean isChatModelExplicitlyConfigured() {
        String model = environment.getProperty(CHAT_MODEL);
        return model != null && !model.isEmpty();
    }

    /**
     * Checks if an embedding model is explicitly configured via properties.
     */
    public boolean isEmbeddingModelExplicitlyConfigured() {
        String model = environment.getProperty(EMBEDDING_MODEL);
        return model != null && !model.isEmpty();
    }

    /**
     * Checks if any OpenAI API key is configured.
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
     * Checks if a Cloud Foundry service has an MCP service URL configured.
     */
    public boolean hasMcpServiceUrl(CfService service) {
        CfCredentials credentials = service.getCredentials();
        return credentials != null && credentials.getString(MCP_SERVICE_URL) != null;
    }

    /**
     * Logs the current configuration state for debugging.
     * Updated to reflect no-defaults behavior.
     */
    public void logConfigurationState() {
        logger.info("=== GenAI Configuration State (No Defaults Applied) ===");
        logger.info("Chat Model Configured: {} (value: '{}')",
                isChatModelExplicitlyConfigured(), getChatModelName());
        logger.info("Embedding Model Configured: {} (value: '{}')",
                isEmbeddingModelExplicitlyConfigured(), getEmbeddingModelName());
        logger.info("API Key Available: {}", hasApiKey());
        logger.info("MCP Service URLs: {}", getMcpServiceUrls());
        logger.info("=== End Configuration State ===");
    }
}