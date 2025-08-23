package org.tanzu.mcpclient.agents;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response message received from AI agents via message queue.
 * Based on the AgentResponse interface specification in AGENTS.md.
 */
public record AgentResponse(
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("agentType") String agentType,
        @JsonProperty("content") String content,
        @JsonProperty("isComplete") Boolean isComplete,
        @JsonProperty("timestamp") long timestamp,
        @JsonProperty("metadata") Object metadata
) {
    /**
     * Creates a simple agent response.
     */
    public static AgentResponse create(String correlationId, String agentType, String content, boolean isComplete) {
        return new AgentResponse(
                correlationId,
                agentType,
                content,
                isComplete,
                System.currentTimeMillis(),
                null
        );
    }

    /**
     * Creates an agent response with metadata.
     */
    public static AgentResponse create(String correlationId, String agentType, String content,
                                       boolean isComplete, Object metadata) {
        return new AgentResponse(
                correlationId,
                agentType,
                content,
                isComplete,
                System.currentTimeMillis(),
                metadata
        );
    }
}