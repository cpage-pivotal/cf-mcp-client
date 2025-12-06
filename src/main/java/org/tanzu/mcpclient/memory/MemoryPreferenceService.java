package org.tanzu.mcpclient.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that manages memory type preferences per conversation ID.
 * Stores whether each conversation should use Transient (MessageWindowChatMemory)
 * or Persistent (VectorStoreChatMemory) memory advisor.
 */
@Service
public class MemoryPreferenceService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryPreferenceService.class);
    
    private final Map<String, MemoryType> preferences = new ConcurrentHashMap<>();

    /**
     * Memory type enumeration
     */
    public enum MemoryType {
        TRANSIENT,  // MessageWindowChatMemory
        PERSISTENT  // VectorStoreChatMemory
    }

    /**
     * Get the memory type preference for a conversation.
     * Defaults to TRANSIENT if no preference is set.
     *
     * @param conversationId the conversation ID
     * @return the memory type preference
     */
    public MemoryType getPreference(String conversationId) {
        MemoryType preference = preferences.getOrDefault(conversationId, MemoryType.TRANSIENT);
        logger.debug("Getting memory preference for conversation {}: {}", conversationId, preference);
        return preference;
    }

    /**
     * Set the memory type preference for a conversation.
     *
     * @param conversationId the conversation ID
     * @param memoryType the memory type to set
     */
    public void setPreference(String conversationId, MemoryType memoryType) {
        logger.info("Setting memory preference for conversation {} to: {}", conversationId, memoryType);
        preferences.put(conversationId, memoryType);
    }

    /**
     * Clear the preference for a conversation.
     *
     * @param conversationId the conversation ID
     */
    public void clearPreference(String conversationId) {
        logger.debug("Clearing memory preference for conversation: {}", conversationId);
        preferences.remove(conversationId);
    }

    /**
     * Get all preferences (primarily for debugging/monitoring).
     *
     * @return map of all preferences
     */
    public Map<String, MemoryType> getAllPreferences() {
        return Map.copyOf(preferences);
    }
}

