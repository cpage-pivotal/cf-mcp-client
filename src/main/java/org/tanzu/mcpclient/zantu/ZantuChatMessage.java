package org.tanzu.mcpclient.zantu;

import java.time.Instant;

public record ZantuChatMessage(
        String messageId,
        String conversationId,
        String content,
        String direction, // "TO_ZANTU" or "FROM_ZANTU"
        Instant timestamp
) {
    public static ZantuChatMessage toZantu(String messageId, String conversationId, String content) {
        return new ZantuChatMessage(messageId, conversationId, content, "TO_ZANTU", Instant.now());
    }

    public static ZantuChatMessage fromZantu(String messageId, String conversationId, String content) {
        return new ZantuChatMessage(messageId, conversationId, content, "FROM_ZANTU", Instant.now());
    }
}