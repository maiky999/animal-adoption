package com.wsb.animaladoption.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String EXCHANGE_EMAIL = "exchange.email";
    public static final String QUEUE_REGISTRATION = "queue.email.registration";
    public static final String ROUTING_KEY_REGISTRATION = "routing.key.registration";
    public static final String QUEUE_CHAT = "chat.queue";
    public static final String EXCHANGE_CHAT = "chat.exchange";
    public static final String ROUTING_KEY_CHAT = "chat.routing.key";

    @Bean
    public Queue chatQueue() {
        return new Queue(QUEUE_CHAT, true);
    }

    @Bean
    public DirectExchange chatExchange() {
        return new DirectExchange(EXCHANGE_CHAT);
    }

    @Bean
    public Binding chatBinding(Queue chatQueue, DirectExchange chatExchange) {
        return BindingBuilder.bind(chatQueue).to(chatExchange).with(ROUTING_KEY_CHAT);
    }

    @Bean
    public TopicExchange emailExchange() {
        return new TopicExchange(EXCHANGE_EMAIL);
    }

    @Bean
    public Queue registrationQueue() {
        return new Queue(QUEUE_REGISTRATION, true);
    }

    @Bean
    public Binding bindingRegistration(Queue registrationQueue, TopicExchange emailExchange) {
        return BindingBuilder.bind(registrationQueue).to(emailExchange).with(ROUTING_KEY_REGISTRATION);
    }

    @Bean
    public MessageConverter messageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        converter.addAllowedListPatterns("com.wsb.animaladoption.*", "java.util.*", "java.lang.*");
        return converter;
    }
}
