package com.ecommerce.order_service.service;

import com.ecommerce.order_service.dto.OrderRequestDto;
import com.ecommerce.order_service.dto.OrderResponseDto;
import com.ecommerce.order_service.event.OrderPlacedEvent;
import com.ecommerce.order_service.exception.ResourceNotFoundException;
import com.ecommerce.order_service.mapper.OrderMapper;
import com.ecommerce.order_service.model.Order;
import com.ecommerce.order_service.model.OrderLineItems;
import com.ecommerce.order_service.model.OrderStatus;
import com.ecommerce.order_service.repository.OrderRepository;
import com.ecommerce.order_service.service.client.InventoryClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
@RefreshScope
public class OrderService implements IOrderService{

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxService outboxService;

    @Value("${order.enabled: true}")
    private boolean ordersEnabled;


    public OrderResponseDto fallbackMethod(OrderRequestDto orderRequest, String userId, Throwable throwable) {

            log.error("Circuit Breaker activado. Causa: {}", throwable.getMessage());

            throw new RuntimeException("El Servicio de Inventario no responde. Por favore intente mas tarde.");
    }

    @Override
    @Transactional
    public OrderResponseDto placeOrder(OrderRequestDto orderRequest, String userId) {

        if (!ordersEnabled) {
            log.warn("Pedido rechazado: Servicio deshabilitado por configuracion.");
            throw new RuntimeException(
                    "El servicio de pedidos esta actualmente en mantenimiento. Intente mas tarde");
        }

        log.info("Colocando nueva orden...");

        Order order = orderMapper.toOrder(orderRequest);

        order.setUserId(userId);
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setStatus(OrderStatus.PLACED);

        Order savedOrder = orderRepository.save(order);

        log.info("Orden guardada con éxito. ID: {}", savedOrder.getId());

        // ========= Creamos el evento =========
        List<OrderPlacedEvent.OrderItemEvent> orderItems =
                order.getOrderLineItemsList().stream()
                        .map(item -> new OrderPlacedEvent.OrderItemEvent(
                                item.getSku(),
                                item.getPrice().toString(),
                                item.getQuantity()
                        )).toList();

        OrderPlacedEvent event = new OrderPlacedEvent(
                savedOrder.getOrderNumber(), orderRequest.getEmail(), orderItems);

        // Variable para saber el processed de OutboxEvent
        boolean sendToRabbit = false;

        try {
            // ====== Publicamos el evento ========
            // Mensaje: order-events, Evento: event, RoutingKey: order-placed
            rabbitTemplate.convertAndSend("order-events", "order.placed", event);
            sendToRabbit = true;
            log.info("Evento enviado a RabbitMQ para la orden: {}", savedOrder.getOrderNumber());

        } catch (AmqpException e) {
            log.error("RabbitMQ caido. El Outbox asegurara el envio posterior para la orden: {}", event.orderNumber());
        }

        // Guardamos el mensaje en Outbox
        outboxService.saveOrderPlacedEvent(event, sendToRabbit);

        return orderMapper.toOrderResponse(savedOrder);


    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponseDto> getOrders(String userId, boolean isAdmin) {

        if(isAdmin) {
            return orderRepository.findAll().stream()
                    .map(orderMapper::toOrderResponse)
                    .toList();
        }

        return orderRepository.findByUserId(userId).stream()
                .map(orderMapper::toOrderResponse)
                .toList();
    }


    @Override
    @Transactional(readOnly = true)
    public OrderResponseDto getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden", "id", id));
        return orderMapper.toOrderResponse(order);
    }

    @Override
    @Transactional
    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new ResourceNotFoundException("Orden", "id", id);
        }
        orderRepository.deleteById(id);
        log.info("Orden eliminada. ID: {}", id);
    }

    @Override
    @Transactional
    public void updateStatus(String orderNumber, OrderStatus newStatus) {

       log.info("Actualizando base de datos: Orden {} -> {}", orderNumber, newStatus);

       orderRepository.findByOrderNumber(orderNumber).ifPresentOrElse(
               order -> {
                   order.setStatus(newStatus);
                   orderRepository.save(order);
                   log.info("Estado actualizado en BD para la orden: {}", orderNumber);
               },
               () -> log.error("No se encontro la orden {} para actualizar", orderNumber)
       );
    }
}
