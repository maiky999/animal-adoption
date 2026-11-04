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

    public static final String QUEUE_ADOPTION_FORM = "queue.email.adoption_form";
    public static final String ROUTING_KEY_ADOPTION_FORM = "routing.key.adoption_form";

    public static final String QUEUE_EMAIL_CHANGE = "queue.email.email_change";
    public static final String ROUTING_KEY_EMAIL_CHANGE = "routing.key.email_change";

    // Chat messages are not sent through the queue - they are saved synchronously (see MessageServiceImpl.sendMessage),
    // so the sender sees their message immediately. The queue is used for emails, where waiting for SMTP
    // would slow down the request and a delay of a few seconds does not matter.

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
    public Queue adoptionFormQueue() {
        return new Queue(QUEUE_ADOPTION_FORM, true);
    }

    @Bean
    public Binding bindingAdoptionForm(Queue adoptionFormQueue, TopicExchange emailExchange) {
        return BindingBuilder.bind(adoptionFormQueue).to(emailExchange).with(ROUTING_KEY_ADOPTION_FORM);
    }

    @Bean
    public Queue emailChangeQueue() {
        return new Queue(QUEUE_EMAIL_CHANGE, true);
    }

    @Bean
    public Binding bindingEmailChange(Queue emailChangeQueue, TopicExchange emailExchange) {
        return BindingBuilder.bind(emailChangeQueue).to(emailExchange).with(ROUTING_KEY_EMAIL_CHANGE);
    }

    @Bean
    public MessageConverter messageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        converter.addAllowedListPatterns("com.wsb.animaladoption.*", "java.util.*", "java.lang.*");
        return converter;
    }
}