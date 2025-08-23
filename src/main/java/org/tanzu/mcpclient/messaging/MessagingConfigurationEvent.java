package org.tanzu.mcpclient.messaging;

import org.springframework.context.ApplicationEvent;

/**
 * Event published when messaging configuration values are available or change.
 * Contains agent status information for monitoring and display purposes.
 */
public class MessagingConfigurationEvent extends ApplicationEvent {

    private final AgentStatus agentStatus;

    public MessagingConfigurationEvent(Object source, AgentStatus agentStatus) {
        super(source);
        this.agentStatus = agentStatus;
    }

    public AgentStatus getAgentStatus() {
        return agentStatus;
    }

    /**
     * Record containing agent messaging system status information.
     * Uses modern Java record syntax for immutable data transfer.
     */
    public record AgentStatus(
            String connectionStatus,
            int activeHandlers,
            String implementation,
            boolean available,
            String message
    ) {
        /**
         * Creates an AgentStatus for when messaging service is not available.
         */
        public static AgentStatus unavailable(String reason) {
            return new AgentStatus(
                    "disconnected",
                    0,
                    "none",
                    false,
                    reason
            );
        }

        /**
         * Creates an AgentStatus for RabbitMQ-based messaging.
         */
        public static AgentStatus rabbitMQ(String connectionStatus, int activeHandlers) {
            return new AgentStatus(
                    connectionStatus,
                    activeHandlers,
                    "rabbitmq",
                    "connected".equals(connectionStatus),
                    "RabbitMQ messaging enabled"
            );
        }

        /**
         * Creates an AgentStatus for in-memory messaging fallback.
         */
        public static AgentStatus inMemory(int activeHandlers) {
            return new AgentStatus(
                    "connected",
                    activeHandlers,
                    "in-memory",
                    true,
                    "In-memory messaging fallback"
            );
        }
    }
}