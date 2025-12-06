package org.tanzu.mcpclient.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tanzu.mcpclient.model.ModelDiscoveryService;
import org.tanzu.mcpclient.vectorstore.VectorStoreConfiguration;

@Configuration
public class MemoryConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MemoryConfiguration.class);

    private final ModelDiscoveryService modelDiscoveryService;

    public MemoryConfiguration(ModelDiscoveryService modelDiscoveryService) {
        this.modelDiscoveryService = modelDiscoveryService;
    }

    /**
     * Creates the Transient memory advisor (MessageWindowChatMemory).
     * This advisor keeps a sliding window of recent messages in memory.
     */
    @Bean
    public MessageChatMemoryAdvisor transientMemoryAdvisor(ChatMemoryRepository chatMemoryRepository) {
        logger.info("Creating Transient memory advisor (MessageWindowChatMemory)");
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    /**
     * Creates the Persistent memory advisor (VectorStoreChatMemory).
     * This advisor uses vector store for semantic memory retrieval.
     */
    @Bean
    public VectorStoreChatMemoryAdvisor persistentMemoryAdvisor(VectorStore vectorStore) {
        logger.info("Creating Persistent memory advisor (VectorStoreChatMemory)");
        return VectorStoreChatMemoryAdvisor.builder(vectorStore).defaultTopK(10).build();
    }

    /**
     * Check if the persistent memory advisor is available.
     * Requires both a valid vector store and an available embedding model.
     */
    public boolean isPersistentMemoryAvailable(VectorStore vectorStore) {
        boolean available = !(vectorStore instanceof VectorStoreConfiguration.EmptyVectorStore) 
                && modelDiscoveryService.isEmbeddingModelAvailable();
        logger.debug("Persistent memory available: {}", available);
        return available;
    }
}