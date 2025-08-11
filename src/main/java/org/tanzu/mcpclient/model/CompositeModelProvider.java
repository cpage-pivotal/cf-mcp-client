package org.tanzu.mcpclient.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Composite service that orchestrates multiple ModelProviders to provide models.
 * Uses priority-based selection where providers with lower priority numbers are tried first.
 * This implements the Provider pattern for extensible model source management.
 */
@Service
public class CompositeModelProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(CompositeModelProvider.class);
    
    private final List<ModelProvider> providers;
    
    /**
     * Constructor that accepts all available ModelProvider implementations.
     * Spring will automatically inject all beans that implement the ModelProvider interface.
     */
    public CompositeModelProvider(List<ModelProvider> providers) {
        this.providers = providers;
        
        // Sort providers by priority (lower numbers = higher priority)
        this.providers.sort(Comparator.comparing(ModelProvider::getPriority));
        
        logger.info("Initialized CompositeModelProvider with {} providers: {}", 
                providers.size(), 
                providers.stream().map(ModelProvider::getProviderName).toList());
    }
    
    /**
     * Gets a ChatModel from the first available provider based on priority order.
     * @return ChatModel from the highest priority provider that has one available
     * @throws IllegalStateException if no provider can provide a ChatModel
     */
    public ChatModel getChatModel() {
        logger.debug("Requesting ChatModel from {} providers in priority order", providers.size());
        
        for (ModelProvider provider : providers) {
            logger.debug("Trying provider '{}' (priority {}) for ChatModel", 
                    provider.getProviderName(), provider.getPriority());
            
            Optional<ChatModel> chatModel = provider.getChatModel();
            if (chatModel.isPresent()) {
                logger.info("ChatModel successfully provided by '{}' provider", provider.getProviderName());
                return chatModel.get();
            } else {
                logger.debug("Provider '{}' cannot provide ChatModel", provider.getProviderName());
            }
        }
        
        throw new IllegalStateException("No ChatModel available from any provider. " +
                "Check that at least one provider is properly configured.");
    }
    
    /**
     * Gets an EmbeddingModel from the first available provider based on priority order.
     * @return EmbeddingModel from the highest priority provider that has one available
     * @throws IllegalStateException if no provider can provide an EmbeddingModel
     */
    public EmbeddingModel getEmbeddingModel() {
        logger.debug("Requesting EmbeddingModel from {} providers in priority order", providers.size());
        
        for (ModelProvider provider : providers) {
            logger.debug("Trying provider '{}' (priority {}) for EmbeddingModel", 
                    provider.getProviderName(), provider.getPriority());
            
            Optional<EmbeddingModel> embeddingModel = provider.getEmbeddingModel();
            if (embeddingModel.isPresent()) {
                logger.info("EmbeddingModel successfully provided by '{}' provider", provider.getProviderName());
                return embeddingModel.get();
            } else {
                logger.debug("Provider '{}' cannot provide EmbeddingModel", provider.getProviderName());
            }
        }
        
        throw new IllegalStateException("No EmbeddingModel available from any provider. " +
                "Check that at least one provider is properly configured.");
    }
    
    /**
     * Checks if any provider can provide a ChatModel.
     * @return true if at least one provider can provide a ChatModel
     */
    public boolean isChatModelAvailable() {
        for (ModelProvider provider : providers) {
            if (provider.getChatModel().isPresent()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if any provider can provide an EmbeddingModel.
     * @return true if at least one provider can provide an EmbeddingModel
     */
    public boolean isEmbeddingModelAvailable() {
        for (ModelProvider provider : providers) {
            if (provider.getEmbeddingModel().isPresent()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gets information about all registered providers for debugging/monitoring.
     * @return list of provider names with their priorities
     */
    public List<String> getProviderInfo() {
        return providers.stream()
                .map(p -> String.format("%s (priority: %d)", p.getProviderName(), p.getPriority()))
                .toList();
    }
}