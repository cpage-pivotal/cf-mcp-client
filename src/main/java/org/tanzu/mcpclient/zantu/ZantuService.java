package org.tanzu.mcpclient.zantu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ZantuService {

    private static final Logger logger = LoggerFactory.getLogger(ZantuService.class);

    private final RabbitTemplate rabbitTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final ConcurrentMap<String, String> conversationMessageMap = new ConcurrentHashMap<>();

    public ZantuService(RabbitTemplate rabbitTemplate, ApplicationEventPublisher eventPublisher) {
        this.rabbitTemplate = rabbitTemplate;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Send a message to Zantu for processing
     */
    public void sendMessageToZantu(String conversationId, String message) {
        String messageId = UUID.randomUUID().toString();
        conversationMessageMap.put(messageId, conversationId);

        ZantuChatMessage zantuMessage = ZantuChatMessage.toZantu(messageId, conversationId, message);

        logger.info("Sending message to Zantu: messageId={}, conversationId={}", messageId, conversationId);

        rabbitTemplate.convertAndSend(
                ZantuRabbitMQConfiguration.ZANTU_EXCHANGE,
                ZantuRabbitMQConfiguration.ZANTU_REQUEST_ROUTING_KEY,
                zantuMessage
        );
    }

    /**
     * Listen for responses from Zantu
     */
    @RabbitListener(queues = ZantuRabbitMQConfiguration.ZANTU_RESPONSE_QUEUE)
    public void handleZantuResponse(ZantuChatMessage responseMessage) {
        logger.info("Received response from Zantu: messageId={}, conversationId={}",
                responseMessage.messageId(), responseMessage.conversationId());

        // Publish an event that the chat controller can listen to
        eventPublisher.publishEvent(new ZantuResponseEvent(this, responseMessage));
    }
}