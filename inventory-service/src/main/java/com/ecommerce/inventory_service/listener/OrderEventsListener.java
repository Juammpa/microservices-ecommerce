package com.ecommerce.inventory_service.listener;

import com.ecommerce.inventory_service.event.OrderCancelledEvent;
import com.ecommerce.inventory_service.event.OrderConfirmedEvent;
import com.ecommerce.inventory_service.event.OrderPlacedEvent;
import com.ecommerce.inventory_service.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventsListener {

    private final InventoryService inventoryService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = "inventory-queue")
    public void handleOrderPlacedEvents(OrderPlacedEvent event) {

        log.info("Evento recibido en Inventorio para Orden: {}", event.orderNumber());

            try {
                // Validar stock en todos los productos
                boolean allProductsInStock = event.items().stream()
                                .allMatch(item -> inventoryService.isInStock(item.sku(),item.quantity()));
                if(!allProductsInStock) {
                    cancelOrder(event,"Stock insuficiente en uno o mas productos.");
                    return;
                }

                // Si tenemos stock, descontamos
                event.items().forEach(item -> {
                    inventoryService.reduceStock(item.sku(), item.quantity());
                    log.info("Stock descontado para SKU: {} - Cantidad: {}", item.sku(), item.quantity());
                });

                // Creamos un evento confirmado
                OrderConfirmedEvent confirmedEvent = new OrderConfirmedEvent(
                        event.orderNumber(), event.email()
                );

               // Informamos en RabbitMQ
                rabbitTemplate.convertAndSend("order-events","order.confirmed", confirmedEvent);

            } catch (Exception e) {
                log.error("Error inesperado: {}", e.getMessage());
                cancelOrder(event, "Error tecnico en el procesamiento de inventario.");
            }

    }

    // Metodo que cancela una orden, indicando la razon
    private void cancelOrder(OrderPlacedEvent event, String reason) {
        OrderCancelledEvent cancelledEvent = new OrderCancelledEvent(
                event.orderNumber(), event.email(), reason
        );

        // Informamos a RabbitMQ
        rabbitTemplate.convertAndSend("order-events", "order.cancelled", cancelledEvent);
    }


}
