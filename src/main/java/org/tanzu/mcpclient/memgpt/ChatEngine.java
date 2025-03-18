package org.tanzu.mcpclient.memgpt;

/**
 * Chat engine interface
 */
public interface ChatEngine {

    /**
     * Initialize the chat engine
     * @param userId The user id.  The id maps to a unique memory context.
     */
    void initialize(String userId);

    /**
     * Executes a chat transaction.
     * @param message The text of the user message.
     * @return The text of the generated response.
     */
    String chat(String message);

}
