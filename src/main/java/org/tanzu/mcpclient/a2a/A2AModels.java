package org.tanzu.mcpclient.a2a;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Application-specific models for A2A integration.
 *
 * Note: Core A2A protocol types (Message, Task, AgentCard, etc.) are now provided by the
 * A2A Java SDK (io.a2a.spec.*). This class only contains application-specific DTOs
 * used for frontend communication.
 */
public class A2AModels {

    /**
     * Status update event for frontend SSE consumption.
     * Used to send task progress updates to the Angular frontend.
     *
     * @param type Event type: "status" (intermediate), "result" (final), or "error"
     * @param state Task state from SDK (e.g., "processing", "completed", "failed")
     * @param statusMessage Intermediate status message (for "status" events)
     * @param responseText Final response text (for "result" events)
     * @param agentName Name of the agent that sent this update
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusUpdate(
        String type,
        String state,
        String statusMessage,
        String responseText,
        String agentName
    ) {}
}
