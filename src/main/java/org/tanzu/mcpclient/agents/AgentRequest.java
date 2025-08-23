package org.tanzu.mcpclient.agents;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Request message sent to AI agents via message queue.
 * Based on the AgentRequest interface specification in AGENTS.md.
 */
public record AgentRequest(
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("agentType") String agentType,
        @JsonProperty("prompt") String prompt,
        @JsonProperty("timestamp") long timestamp,
        @JsonProperty("userId") String userId,
        @JsonProperty("context") Object context
) {
    public static AgentRequest create(String correlationId, String agentType, String prompt, String userId) {
        return new AgentRequest(
                correlationId,
                agentType,
                prompt,
                Instant.now().toEpochMilli(),
                userId,
                null
        );
    }
}