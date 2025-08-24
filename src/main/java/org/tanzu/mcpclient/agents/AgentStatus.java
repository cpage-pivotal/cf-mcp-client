package org.tanzu.mcpclient.agents;

public enum AgentStatus {
    /**
     * Agent is available to accept new requests.
     */
    AVAILABLE,

    /**
     * Agent is currently processing requests.
     */
    BUSY,

    /**
     * Agent is offline or unreachable.
     */
    OFFLINE,

    /**
     * Agent encountered an error or is in error state.
     */
    ERROR;

    /**
     * Checks if this status indicates the agent can accept work.
     */
    public boolean canAcceptWork() {
        return this == AVAILABLE;
    }

    /**
     * Gets a lowercase string representation suitable for CSS classes or APIs.
     */
    public String toLowerCase() {
        return name().toLowerCase();
    }
}