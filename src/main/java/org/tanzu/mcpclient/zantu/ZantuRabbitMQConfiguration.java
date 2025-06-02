package org.tanzu.mcpclient.zantu;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZantuRabbitMQConfiguration {

    public static final String ZANTU_REQUEST_QUEUE = "zantu.requests";
    public static final String ZANTU_RESPONSE_QUEUE = "zantu.responses";
    public static final String ZANTU_EXCHANGE = "zantu.exchange";
    public static final String ZANTU_REQUEST_ROUTING_KEY = "zantu.request";
    public static final String ZANTU_RESPONSE_ROUTING_KEY = "zantu.response";

    @Bean
    public TopicExchange zantuExchange() {
        return new TopicExchange(ZANTU_EXCHANGE);
    }

    @Bean
    public Queue zantuRequestQueue() {
        return QueueBuilder.durable(ZANTU_REQUEST_QUEUE).build();
    }

    @Bean
    public Queue zantuResponseQueue() {
        return QueueBuilder.durable(ZANTU_RESPONSE_QUEUE).build();
    }

    @Bean
    public Binding zantuRequestBinding() {
        return BindingBuilder
                .bind(zantuRequestQueue())
                .to(zantuExchange())
                .with(ZANTU_REQUEST_ROUTING_KEY);
    }

    @Bean
    public Binding zantuResponseBinding() {
        return BindingBuilder
                .bind(zantuResponseQueue())
                .to(zantuExchange())
                .with(ZANTU_RESPONSE_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate zantuRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}