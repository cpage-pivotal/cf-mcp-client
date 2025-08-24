package org.tanzu.mcpclient.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Simplified RabbitMQ configuration that declares queues directly as @Bean methods.
 * This avoids all timing issues by letting Spring manage queue creation automatically.
 */
@Configuration
@Conditional(RabbitMQAvailableCondition.class)
public class RabbitMQConfig {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConfig.class);

    // Single exchange for all agent communication
    public static final String AGENT_EXCHANGE = "agents";

    /**
     * Single exchange for all agent types.
     */
    @Bean
    public DirectExchange agentExchange() {
        return new DirectExchange(AGENT_EXCHANGE, true, false);
    }

    // =====================================
    // REVIEWER AGENT QUEUES
    // =====================================

    @Bean
    public Queue reviewerRequestQueue() {
        return QueueBuilder.durable("reviewer.request")
                .withArgument("x-message-ttl", 300000) // 5 minutes TTL
                .build();
    }

    @Bean
    public Queue reviewerReplyQueue() {
        return QueueBuilder.durable("reviewer.reply")
                .withArgument("x-message-ttl", 300000) // 5 minutes TTL
                .build();
    }

    @Bean
    public Binding reviewerRequestBinding() {
        return BindingBuilder
                .bind(reviewerRequestQueue())
                .to(agentExchange())
                .with("reviewer.request");
    }

    @Bean
    public Binding reviewerReplyBinding() {
        return BindingBuilder
                .bind(reviewerReplyQueue())
                .to(agentExchange())
                .with("reviewer.reply");
    }

    // =====================================
    // EDITOR AGENT QUEUES (if you need them)
    // =====================================

    @Bean
    public Queue editorRequestQueue() {
        return QueueBuilder.durable("editor.request")
                .withArgument("x-message-ttl", 300000) // 5 minutes TTL
                .build();
    }

    @Bean
    public Queue editorReplyQueue() {
        return QueueBuilder.durable("editor.reply")
                .withArgument("x-message-ttl", 300000) // 5 minutes TTL
                .build();
    }

    @Bean
    public Binding editorRequestBinding() {
        return BindingBuilder
                .bind(editorRequestQueue())
                .to(agentExchange())
                .with("editor.request");
    }

    @Bean
    public Binding editorReplyBinding() {
        return BindingBuilder
                .bind(editorReplyQueue())
                .to(agentExchange())
                .with("editor.reply");
    }


    // =====================================
    // UTILITY METHODS
    // =====================================

    /**
     * Get request routing key for an agent type.
     */
    public static String getRequestRoutingKey(String agentType) {
        return agentType + ".request";
    }

    /**
     * Get reply routing key for an agent type.
     */
    public static String getReplyRoutingKey(String agentType) {
        return agentType + ".reply";
    }

    // =====================================
    // STANDARD RABBITMQ BEANS
    // =====================================

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }
}