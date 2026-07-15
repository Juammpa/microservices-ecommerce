package com.ecommerce.order_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "order-events";

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public TopicExchange orderEventsExchange(){
        return new TopicExchange(EXCHANGE_NAME);
    }

    // ========== Ordenes confirmadas =========
    @Bean
    public Queue orderConfirmedQueue() {
        return new Queue("order-confirmed-queue", true);
    }

    // Si recibimos routingKey = order.confirmed, se almacena en la cola order-cancelled-confirmed
    @Bean
    public Binding confirmedBinding(Queue orderConfirmedQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(orderConfirmedQueue)
                .to(orderEventsExchange)
                .with("order.confirmed");
    }

    // ========= Ordenes canceladas ===========
    @Bean
    public Queue orderCancelledQueue() {
        return new Queue("order-cancelled-queue", true);
    }

    // Si recibimos routingKey = order.cancelled, se almacena en la cola order-cancelled-queue
    @Bean
    public Binding cancelledBinding(Queue orderCancelledQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(orderCancelledQueue)
                .to(orderEventsExchange)
                .with("order.cancelled");
    }

}
