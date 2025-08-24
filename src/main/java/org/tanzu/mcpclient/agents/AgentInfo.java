package org.tanzu.mcpclient.agents;

/**
 * Agent information record for API responses.
 * Modern Java record with complete agent metadata.
 */
public record AgentInfo(
        String id,
        String name,
        String description,
        AgentStatus status,
        String icon,
        String color
) {

    /**
     * Creates an AgentInfo with available status.
     */
    public static AgentInfo available(String id, String name, String description, String icon, String color) {
        return new AgentInfo(id, name, description, AgentStatus.AVAILABLE, icon, color);
    }

    /**
     * Creates an AgentInfo with busy status.
     */
    public static AgentInfo busy(String id, String name, String description, String icon, String color) {
        return new AgentInfo(id, name, description, AgentStatus.BUSY, icon, color);
    }

    /**
     * Creates an AgentInfo with offline status.
     */
    public static AgentInfo offline(String id, String name, String description, String icon, String color) {
        return new AgentInfo(id, name, description, AgentStatus.OFFLINE, icon, color);
    }

    /**
     * Creates an AgentInfo with error status.
     */
    public static AgentInfo error(String id, String name, String description, String icon, String color) {
        return new AgentInfo(id, name, description, AgentStatus.ERROR, icon, color);
    }

    /**
     * Creates a copy of this AgentInfo with a different status.
     */
    public AgentInfo withStatus(AgentStatus newStatus) {
        return new AgentInfo(id, name, description, newStatus, icon, color);
    }

    /**
     * Checks if this agent is available for work.
     */
    public boolean isAvailable() {
        return status == AgentStatus.AVAILABLE;
    }

    /**
     * Gets a display-friendly status string.
     */
    public String getStatusDisplay() {
        return switch (status) {
            case AVAILABLE -> "Available";
            case BUSY -> "Busy";
            case OFFLINE -> "Offline";
            case ERROR -> "Error";
        };
    }
}
