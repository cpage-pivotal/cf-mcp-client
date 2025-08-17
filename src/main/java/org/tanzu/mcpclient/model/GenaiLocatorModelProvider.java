package org.tanzu.mcpclient.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * ModelProvider implementation that provides models from multiple GenaiLocator instances.
 * This has the highest priority since GenaiLocator provides fully managed models
 * with authentication and configuration handled externally.
 */
@Component
public class GenaiLocatorModelProvider implements ModelProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(GenaiLocatorModelProvider.class);
    private static final int PRIORITY = 0; // Highest priority
    
    private final MultiGenaiLocatorAggregator aggregator;
    
    /**
     * Constructor with MultiGenaiLocatorAggregator injection.
     * The aggregator handles multiple GenaiLocator instances and provides unified access.
     */
    public GenaiLocatorModelProvider(MultiGenaiLocatorAggregator aggregator) {
        this.aggregator = aggregator;
        
        if (aggregator.hasAnyLocators()) {
            logger.debug("MultiGenaiLocatorAggregator available with {} locators - will provide managed models", 
                    aggregator.getLocatorCount());
        } else {
            logger.debug("No GenaiLocator instances available - this provider will not provide models");
        }
    }
    
    @Override
    public Optional<ChatModel> getChatModel() {
        if (!aggregator.hasAnyLocators()) {
            logger.debug("No GenaiLocator instances available, cannot provide ChatModel");
            return Optional.empty();
        }
        
        try {
            Optional<ChatModel> chatModel = aggregator.getFirstAvailableChatModel();
            if (chatModel.isPresent()) {
                logger.debug("Successfully obtained ChatModel from aggregated GenaiLocators: {}", 
                        chatModel.get().getClass().getSimpleName());
                return chatModel;
            } else {
                logger.debug("No ChatModel available from any GenaiLocator");
                return Optional.empty();
            }
        } catch (Exception e) {
            logger.debug("Error obtaining ChatModel from aggregated GenaiLocators: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<EmbeddingModel> getEmbeddingModel() {
        if (!aggregator.hasAnyLocators()) {
            logger.debug("No GenaiLocator instances available, cannot provide EmbeddingModel");
            return Optional.empty();
        }
        
        try {
            Optional<EmbeddingModel> embeddingModel = aggregator.getFirstAvailableEmbeddingModel();
            if (embeddingModel.isPresent()) {
                logger.debug("Successfully obtained EmbeddingModel from aggregated GenaiLocators: {}", 
                        embeddingModel.get().getClass().getSimpleName());
                return embeddingModel;
            } else {
                logger.debug("No EmbeddingModel available from any GenaiLocator");
                return Optional.empty();
            }
        } catch (Exception e) {
            logger.debug("Error obtaining EmbeddingModel from aggregated GenaiLocators: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    @Override
    public int getPriority() {
        return PRIORITY;
    }
    
    @Override
    public String getProviderName() {
        return "MultiGenaiLocator(" + aggregator.getLocatorCount() + " locators)";
    }
}