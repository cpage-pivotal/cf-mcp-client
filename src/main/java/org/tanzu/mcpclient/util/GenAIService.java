package org.tanzu.mcpclient.util;

import io.pivotal.cfenv.core.CfCredentials;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service for detecting and getting information about GenAI services.
 * Updated to apply the same defaults as SpringAIManualConfiguration when API key is available.
 */
@Service
public class GenAIService {
    private static final Logger logger = LoggerFactory.getLogger(GenAIService.class);

    // Constants
    public static final String MCP_SERVICE_URL = "mcpServiceURL";

    public static final String CHAT_MODEL = "spring.ai.openai.chat.options.model";
    public static final String EMBEDDING_MODEL = "spring.ai.openai.embedding.options.model";

    // Default model names that match SpringAIManualConfiguration
    private static final String DEFAULT_CHAT_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";

    private final Environment environment;
    private final CfEnv cfEnv;

    public GenAIService(Environment environment) {
        this.environment = environment;
        this.cfEnv = new CfEnv();
    }

    public boolean isEmbeddingModelAvailable() {
        return environment.getProperty(EMBEDDING_MODEL) != null;
    }

    /**
     * Gets the embedding model name. Applies defaults when API key is available
     * but model not specified, otherwise returns empty for graceful degradation.
     */
    public String getEmbeddingModelName() {
        String model = environment.getProperty(EMBEDDING_MODEL);

        // If explicitly configured, return it
        if (model != null && !model.isEmpty()) {
            return model;
        }

        // If not configured but we have an API key, apply default (same as manual config)
        if (hasApiKey()) {
            logger.debug("No embedding model configured but API key available, using default: {}", DEFAULT_EMBEDDING_MODEL);
            return DEFAULT_EMBEDDING_MODEL;
        }

        // No API key - graceful degradation
        return "";
    }

    /**
     * Gets the chat model name. Applies defaults when API key is available
     * but model not specified, otherwise returns empty for graceful degradation.
     */
    public String getChatModelName() {
        String model = environment.getProperty(CHAT_MODEL);

        // If explicitly configured, return it
        if (model != null && !model.isEmpty()) {
            return model;
        }

        // If not configured but we have an API key, apply default (same as manual config)
        if (hasApiKey()) {
            logger.debug("No chat model configured but API key available, using default: {}", DEFAULT_CHAT_MODEL);
            return DEFAULT_CHAT_MODEL;
        }

        // No API key - graceful degradation
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
    private boolean hasApiKey() {
        // Check in order: specific chat key, specific embedding key, general key
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

    public boolean hasMcpServiceUrl(CfService service) {
        CfCredentials credentials = service.getCredentials();
        return credentials != null && credentials.getString(MCP_SERVICE_URL) != null;
    }
}