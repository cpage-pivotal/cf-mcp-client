package org.tanzu.mcpclient.model;

import io.pivotal.cfenv.boot.genai.GenaiLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * ModelProvider implementation that provides models from GenaiLocator.
 * This has the highest priority since GenaiLocator provides fully managed models
 * with authentication and configuration handled externally.
 */
@Component
public class GenaiLocatorModelProvider implements ModelProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(GenaiLocatorModelProvider.class);
    private static final int PRIORITY = 0; // Highest priority
    
    private final GenaiLocator genaiLocator; // May be null if not available
    
    /**
     * Constructor with optional GenaiLocator injection.
     * GenaiLocator is only available when GenaiLocatorAutoConfiguration is active.
     */
    public GenaiLocatorModelProvider(@Nullable GenaiLocator genaiLocator) {
        this.genaiLocator = genaiLocator;
        
        if (genaiLocator != null) {
            logger.debug("GenaiLocator available - will provide managed models");
        } else {
            logger.debug("GenaiLocator not available - this provider will not provide models");
        }
    }
    
    @Override
    public Optional<ChatModel> getChatModel() {
        if (genaiLocator == null) {
            logger.debug("GenaiLocator not available, cannot provide ChatModel");
            return Optional.empty();
        }
        
        try {
            ChatModel chatModel = genaiLocator.getFirstAvailableChatModel();
            logger.debug("Successfully obtained ChatModel from GenaiLocator: {}", 
                    chatModel.getClass().getSimpleName());
            return Optional.of(chatModel);
        } catch (Exception e) {
            logger.debug("No ChatModel available from GenaiLocator: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<EmbeddingModel> getEmbeddingModel() {
        if (genaiLocator == null) {
            logger.debug("GenaiLocator not available, cannot provide EmbeddingModel");
            return Optional.empty();
        }
        
        try {
            EmbeddingModel embeddingModel = genaiLocator.getFirstAvailableEmbeddingModel();
            logger.debug("Successfully obtained EmbeddingModel from GenaiLocator: {}", 
                    embeddingModel.getClass().getSimpleName());
            return Optional.of(embeddingModel);
        } catch (Exception e) {
            logger.debug("No EmbeddingModel available from GenaiLocator: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    @Override
    public int getPriority() {
        return PRIORITY;
    }
    
    @Override
    public String getProviderName() {
        return "GenaiLocator";
    }
}