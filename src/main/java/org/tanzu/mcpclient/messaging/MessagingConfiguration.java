package org.tanzu.mcpclient.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.tanzu.mcpclient.agents.AgentMessageServiceInterface;
import org.tanzu.mcpclient.messaging.MessagingConfigurationEvent.AgentStatus;

/**
 * Configuration class that publishes messaging configuration events.
 * Follows the same pattern as DocumentConfiguration for consistency.
 */
@Configuration
public class MessagingConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MessagingConfiguration.class);

    private final AgentMessageServiceInterface agentMessageService;
    private final ApplicationEventPublisher eventPublisher;

    public MessagingConfiguration(@Autowired(required = false) AgentMessageServiceInterface agentMessageService,
                                  ApplicationEventPublisher eventPublisher) {
        this.agentMessageService = agentMessageService;
        this.eventPublisher = eventPublisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void publishConfigurationEvent() {
        AgentStatus agentStatus = determineAgentStatus();

        logger.debug("Publishing MessagingConfigurationEvent: status={}, implementation={}, available={}",
                agentStatus.connectionStatus(), agentStatus.implementation(), agentStatus.available());

        eventPublisher.publishEvent(new MessagingConfigurationEvent(this, agentStatus));
    }

    /**
     * Determines the current agent status based on available messaging service.
     */
    private AgentStatus determineAgentStatus() {
        if (agentMessageService == null) {
            return AgentStatus.unavailable("Agent messaging service not configured");
        }

        try {
            var connectionStatus = agentMessageService.getConnectionStatus();
            String status = (String) connectionStatus.getOrDefault("connectionStatus", "unknown");
            Integer activeHandlers = (Integer) connectionStatus.getOrDefault("activeHandlers", 0);
            String implementation = (String) connectionStatus.getOrDefault("implementation", "unknown");

            return switch (implementation) {
                case "rabbitmq" -> AgentStatus.rabbitMQ(status, activeHandlers);
                case "in-memory" -> AgentStatus.inMemory(activeHandlers);
                default -> AgentStatus.unavailable("Unknown messaging implementation: " + implementation);
            };

        } catch (Exception e) {
            logger.warn("Failed to determine agent status: {}", e.getMessage());
            return AgentStatus.unavailable("Failed to determine status: " + e.getMessage());
        }
    }
}