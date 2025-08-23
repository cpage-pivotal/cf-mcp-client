package org.tanzu.mcpclient.messaging;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.lang.NonNull;

/**
 * Condition that matches when RabbitMQ server is NOT available.
 */
public class RabbitMQNotAvailableCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, @NonNull AnnotatedTypeMetadata metadata) {
        return !RabbitMQAvailabilityUtil.isRabbitMQAvailable(context.getEnvironment());
    }
}