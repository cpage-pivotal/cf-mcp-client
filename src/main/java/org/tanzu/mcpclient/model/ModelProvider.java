package org.tanzu.mcpclient.model;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.Optional;

public interface ModelProvider {
    
    /**
     * Gets a chat model from this provider if available.
     * @return Optional containing the ChatModel, or empty if not available from this provider
     */
    Optional<ChatModel> getChatModel();
    
    /**
     * Gets an embedding model from this provider if available.
     * @return Optional containing the EmbeddingModel, or empty if not available from this provider
     */
    Optional<EmbeddingModel> getEmbeddingModel();
    
    /**
     * Gets the priority of this provider. Lower numbers indicate higher priority.
     * @return priority value, where 0 is highest priority
     */
    int getPriority();
    
    /**
     * Gets a human-readable name for this provider for logging and debugging.
     * @return provider name
     */
    String getProviderName();
}