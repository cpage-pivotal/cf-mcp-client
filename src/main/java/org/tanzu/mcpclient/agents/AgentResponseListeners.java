package org.tanzu.mcpclient.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import org.tanzu.mcpclient.messaging.RabbitMQAvailableCondition;

/**
 * Simplified agent response listeners.
 * Since queues are now declared as @Bean methods, Spring creates them before
 * listeners try to connect - no timing issues!
 */
@Service
@Conditional(RabbitMQAvailableCondition.class)
public class AgentResponseListeners {

    private static final Logger logger = LoggerFactory.getLogger(AgentResponseListeners.class);

    private final AgentMessageService messageService;

    public AgentResponseListeners(AgentMessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Listener for reviewer agent responses.
     * Queue is created as @Bean so it exists before this listener starts.
     */
    @RabbitListener(queues = "reviewer.reply")
    public void handleReviewerResponse(AgentResponse response) {
        logger.debug("Received reviewer response: {}", response.correlationId());
        messageService.handleAgentResponse(response);
    }

    /**
     * Listener for editor agent responses.
     * Only uncomment if you're using editor agents.
     */
    @RabbitListener(queues = "editor.reply")
    public void handleEditorResponse(AgentResponse response) {
        logger.debug("Received editor response: {}", response.correlationId());
        messageService.handleAgentResponse(response);
    }

    // Add more listeners here as needed for new agent types
    // Just follow the same pattern:
    //
    // @RabbitListener(queues = "{agentType}.reply")
    // public void handle{AgentType}Response(AgentResponse response) {
    //     logger.debug("Received {agentType} response: {}", response.correlationId());
    //     messageService.handleAgentResponse(response);
    // }
}