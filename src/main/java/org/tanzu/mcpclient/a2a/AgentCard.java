package org.tanzu.mcpclient.a2a;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Java record representing the A2A Agent Card structure.
 * Agent Cards are discovered at /.well-known/agent.json endpoints.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentCard(
    String protocolVersion,
    String name,
    String description,
    String url,
    String version,
    String preferredTransport,
    AgentCapabilities capabilities,
    List<String> defaultInputModes,
    List<String> defaultOutputModes,
    List<AgentSkill> skills,
    AgentProvider provider
) {
    /**
     * Nested record representing agent provider information
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AgentProvider(
        String organization,
        String url
    ) {}

    /**
     * Nested record representing agent capabilities
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AgentCapabilities(
        boolean streaming,
        boolean pushNotifications,
        boolean stateTransitionHistory
    ) {}

    /**
     * Nested record representing an agent skill
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AgentSkill(
        String id,
        String name,
        String description,
        List<String> tags,
        List<SkillExample> examples
    ) {}

    /**
     * Nested record representing a skill example
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkillExample(
        String input,
        String output,
        String description
    ) {}
}
