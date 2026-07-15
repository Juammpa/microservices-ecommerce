package com.ecommerce.inventory_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public Queue inventoryQueue() {
        return new Queue("inventory-queue", true);
    }

    @Bean
    public TopicExchange orderEventsExchange(){
        return new TopicExchange("order-events");
    }

    // Regla: T0do lo que venga con la routingKey order-placed se envia a inventory-queue
    @Bean
    public Binding binding(Queue inventoryQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(inventoryQueue).to(orderEventsExchange).with("order.placed");
    }



}
