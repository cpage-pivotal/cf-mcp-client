package org.tanzu.mcpclient.model;

import io.pivotal.cfenv.boot.genai.GenaiLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Component that aggregates models from multiple GenaiLocator instances.
 * Provides unified access to chat and embedding models from all available locators,
 * with proper error handling and logging for individual locator failures.
 */
@Component
public class MultiGenaiLocatorAggregator {
    
    private static final Logger logger = LoggerFactory.getLogger(MultiGenaiLocatorAggregator.class);
    
    private final List<GenaiLocator> genaiLocators;
    
    /**
     * Constructor with list injection of GenaiLocator beans.
     * If no GenaiLocator beans are available, an empty list is used.
     * 
     * @param genaiLocators List of GenaiLocator beans injected by Spring
     */
    public MultiGenaiLocatorAggregator(List<GenaiLocator> genaiLocators) {
        this.genaiLocators = genaiLocators != null ? new ArrayList<>(genaiLocators) : List.of();
        
        if (this.genaiLocators.isEmpty()) {
            logger.debug("No GenaiLocator beans available - aggregator will provide empty results");
        } else {
            logger.debug("Initialized with {} GenaiLocator beans", this.genaiLocators.size());
            for (int i = 0; i < this.genaiLocators.size(); i++) {
                GenaiLocator locator = this.genaiLocators.get(i);
                logger.debug("GenaiLocator[{}]: {}", i, locator.getClass().getSimpleName());
            }
        }
    }
    
    /**
     * Aggregates chat models from all GenaiLocator instances.
     * Collects the first available chat model from each locator, handling failures gracefully.
     * 
     * @return List of all available chat models from all locators
     */
    public List<ChatModel> aggregateChatModels() {
        List<ChatModel> aggregatedModels = new ArrayList<>();
        
        for (int i = 0; i < genaiLocators.size(); i++) {
            GenaiLocator locator = genaiLocators.get(i);
            try {
                ChatModel chatModel = locator.getFirstAvailableChatModel();
                if (chatModel != null) {
                    aggregatedModels.add(chatModel);
                    logger.debug("Added chat model from GenaiLocator[{}]: {}", i, chatModel.getClass().getSimpleName());
                }
            } catch (Exception e) {
                logger.warn("Failed to get chat model from GenaiLocator[{}]: {}", i, e.getMessage());
            }
        }
        
        logger.debug("Aggregated {} chat models from {} locators", aggregatedModels.size(), genaiLocators.size());
        return aggregatedModels;
    }
    
    /**
     * Aggregates embedding models from all GenaiLocator instances.
     * Collects the first available embedding model from each locator, handling failures gracefully.
     * 
     * @return List of all available embedding models from all locators
     */
    public List<EmbeddingModel> aggregateEmbeddingModels() {
        List<EmbeddingModel> aggregatedModels = new ArrayList<>();
        
        for (int i = 0; i < genaiLocators.size(); i++) {
            GenaiLocator locator = genaiLocators.get(i);
            try {
                EmbeddingModel embeddingModel = locator.getFirstAvailableEmbeddingModel();
                if (embeddingModel != null) {
                    aggregatedModels.add(embeddingModel);
                    logger.debug("Added embedding model from GenaiLocator[{}]: {}", i, embeddingModel.getClass().getSimpleName());
                }
            } catch (Exception e) {
                logger.warn("Failed to get embedding model from GenaiLocator[{}]: {}", i, e.getMessage());
            }
        }
        
        logger.debug("Aggregated {} embedding models from {} locators", aggregatedModels.size(), genaiLocators.size());
        return aggregatedModels;
    }
    
    /**
     * Gets the first available chat model from all locators.
     * Uses failover strategy - tries first locator, then subsequent ones if needed.
     * 
     * @return Optional containing the first available ChatModel, or empty if none available
     */
    public Optional<ChatModel> getFirstAvailableChatModel() {
        for (int i = 0; i < genaiLocators.size(); i++) {
            GenaiLocator locator = genaiLocators.get(i);
            try {
                ChatModel chatModel = locator.getFirstAvailableChatModel();
                if (chatModel != null) {
                    logger.debug("Got first available chat model from GenaiLocator[{}]: {}", 
                            i, chatModel.getClass().getSimpleName());
                    return Optional.of(chatModel);
                }
            } catch (Exception e) {
                logger.debug("No chat model available from GenaiLocator[{}]: {}", i, e.getMessage());
            }
        }
        
        logger.debug("No chat models available from any of {} locators", genaiLocators.size());
        return Optional.empty();
    }
    
    /**
     * Gets the first available embedding model from all locators.
     * Uses failover strategy - tries first locator, then subsequent ones if needed.
     * 
     * @return Optional containing the first available EmbeddingModel, or empty if none available
     */
    public Optional<EmbeddingModel> getFirstAvailableEmbeddingModel() {
        for (int i = 0; i < genaiLocators.size(); i++) {
            GenaiLocator locator = genaiLocators.get(i);
            try {
                EmbeddingModel embeddingModel = locator.getFirstAvailableEmbeddingModel();
                if (embeddingModel != null) {
                    logger.debug("Got first available embedding model from GenaiLocator[{}]: {}", 
                            i, embeddingModel.getClass().getSimpleName());
                    return Optional.of(embeddingModel);
                }
            } catch (Exception e) {
                logger.debug("No embedding model available from GenaiLocator[{}]: {}", i, e.getMessage());
            }
        }
        
        logger.debug("No embedding models available from any of {} locators", genaiLocators.size());
        return Optional.empty();
    }
    
    /**
     * Aggregates model names by capability from all locators.
     * 
     * @param capability The capability to search for (e.g., "CHAT", "EMBEDDING")
     * @return List of all model names with the specified capability from all locators
     */
    public List<String> aggregateModelNamesByCapability(String capability) {
        List<String> aggregatedNames = new ArrayList<>();
        
        for (int i = 0; i < genaiLocators.size(); i++) {
            GenaiLocator locator = genaiLocators.get(i);
            try {
                List<String> modelNames = locator.getModelNamesByCapability(capability);
                if (modelNames != null && !modelNames.isEmpty()) {
                    aggregatedNames.addAll(modelNames);
                    logger.debug("Added {} models with capability '{}' from GenaiLocator[{}]", 
                            modelNames.size(), capability, i);
                }
            } catch (Exception e) {
                logger.warn("Failed to get models with capability '{}' from GenaiLocator[{}]: {}", 
                        capability, i, e.getMessage());
            }
        }
        
        logger.debug("Aggregated {} model names with capability '{}' from {} locators", 
                aggregatedNames.size(), capability, genaiLocators.size());
        return aggregatedNames;
    }
    
    /**
     * Checks if any locators are available.
     * 
     * @return true if at least one GenaiLocator is available, false otherwise
     */
    public boolean hasAnyLocators() {
        return !genaiLocators.isEmpty();
    }
    
    /**
     * Gets the number of available GenaiLocator instances.
     * 
     * @return number of GenaiLocator beans
     */
    public int getLocatorCount() {
        return genaiLocators.size();
    }
}