package org.tanzu.mcpclient.messaging;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.lang.NonNull;

/**
 * Condition that matches when RabbitMQ classes are available on classpath
 * AND the server is reachable.
 */
public class RabbitMQReadyCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, @NonNull AnnotatedTypeMetadata metadata) {
        // Check if RabbitMQ classes are on classpath
        try {
            Class.forName("org.springframework.amqp.rabbit.core.RabbitTemplate");
            Class.forName("com.rabbitmq.client.ConnectionFactory");
        } catch (ClassNotFoundException e) {
            return false;
        }

        // Check if RabbitMQ server is available
        return RabbitMQAvailabilityUtil.isRabbitMQAvailable(context.getEnvironment());
    }
}