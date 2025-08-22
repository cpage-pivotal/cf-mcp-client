package org.tanzu.mcpclient.agents;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Response message received from AI agents via message queue.
 * Based on the AgentResponse interface specification in AGENTS.md.
 * Simplified to only use isComplete flag (isPartial was redundant).
 */
public record AgentResponse(
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("agentType") String agentType,
        @JsonProperty("content") String content,
        @JsonProperty("timestamp") long timestamp,
        @JsonProperty("isComplete") Boolean isComplete,
        @JsonProperty("metadata") Object metadata
) {
    public static AgentResponse create(String correlationId, String agentType, String content) {
        return new AgentResponse(
                correlationId,
                agentType,
                content,
                Instant.now().toEpochMilli(),
                true,
                null
        );
    }

    public static AgentResponse createPartial(String correlationId, String agentType, String content) {
        return new AgentResponse(
                correlationId,
                agentType,
                content,
                Instant.now().toEpochMilli(),
                false, // partial = not complete
                null
        );
    }

    public static AgentResponse createComplete(String correlationId, String agentType, String content) {
        return new AgentResponse(
                correlationId,
                agentType,
                content,
                Instant.now().toEpochMilli(),
                true,
                null
        );
    }

    public static AgentResponse createWithMetadata(String correlationId, String agentType, String content, Object metadata) {
        return new AgentResponse(
                correlationId,
                agentType,
                content,
                Instant.now().toEpochMilli(),
                true,
                metadata
        );
    }
}