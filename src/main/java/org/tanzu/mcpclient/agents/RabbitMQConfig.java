package org.tanzu.mcpclient.agents;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for agent messaging.
 * Sets up queues and exchanges for agent communication as specified in AGENTS.md.
 */
@Configuration
@ConditionalOnClass(ConnectionFactory.class)
public class RabbitMQConfig {

    // Queue names as specified in AGENTS.md
    public static final String AGENT_REQUEST_QUEUE = "agent.reviewer.request";
    public static final String AGENT_REPLY_QUEUE = "agent.reviewer.reply";
    
    // Exchange for agent messaging
    public static final String AGENT_EXCHANGE = "agent.exchange";
    
    // Routing keys
    public static final String REVIEWER_REQUEST_ROUTING_KEY = "reviewer.request";
    public static final String REVIEWER_REPLY_ROUTING_KEY = "reviewer.reply";

    @Bean
    public DirectExchange agentExchange() {
        return new DirectExchange(AGENT_EXCHANGE);
    }

    @Bean
    public Queue agentRequestQueue() {
        return QueueBuilder.durable(AGENT_REQUEST_QUEUE)
                .withArgument("x-message-ttl", 300000) // 5 minutes TTL
                .build();
    }

    @Bean
    public Queue agentReplyQueue() {
        return QueueBuilder.durable(AGENT_REPLY_QUEUE)
                .withArgument("x-message-ttl", 300000) // 5 minutes TTL
                .build();
    }

    @Bean
    public Binding requestBinding() {
        return BindingBuilder
                .bind(agentRequestQueue())
                .to(agentExchange())
                .with(REVIEWER_REQUEST_ROUTING_KEY);
    }

    @Bean
    public Binding replyBinding() {
        return BindingBuilder
                .bind(agentReplyQueue())
                .to(agentExchange())
                .with(REVIEWER_REPLY_ROUTING_KEY);
    }

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