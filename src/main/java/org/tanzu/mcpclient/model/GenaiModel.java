package org.tanzu.mcpclient.model;

/**
 * Configuration information for AI models including source tracking.
 * This encapsulates all the information needed to create and configure AI models.
 */
public record GenaiModel(
        String modelName,
        String apiKey,
        String baseUrl,
        ModelSource source
) {
    /**
     * Checks if this configuration has all required values to create a functional model.
     */
    public boolean isValid() {
        return modelName != null && !modelName.isEmpty() 
            && apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Checks if this configuration comes from GenaiLocator.
     */
    public boolean isFromGenaiLocator() {
        return source == ModelSource.GENAI_LOCATOR;
    }

    /**
     * Checks if this configuration comes from properties.
     */
    public boolean isFromProperties() {
        return source == ModelSource.PROPERTIES;
    }

    /**
     * Creates a ModelConfig with default "no-model-configured" values for invalid configurations.
     */
    public static GenaiModel createDefault(ModelSource source) {
        return new GenaiModel(
            "no-model-configured",
            "no-api-key-configured",
            "https://api.openai.com",
            source
        );
    }
}