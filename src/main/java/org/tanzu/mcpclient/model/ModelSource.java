package org.tanzu.mcpclient.model;

/**
 * Enum representing the source of model configuration.
 */
public enum ModelSource {
    /**
     * Model configuration comes from GenaiLocator (Tanzu Platform 10.2+ format).
     */
    GENAI_LOCATOR,
    
    /**
     * Model configuration comes from traditional Spring AI properties.
     */
    PROPERTIES
}