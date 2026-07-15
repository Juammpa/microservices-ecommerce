package com.ecommerce.order_service.service;

import com.ecommerce.order_service.event.OrderPlacedEvent;
import com.ecommerce.order_service.model.OutboxEvent;

import java.util.List;

public interface IOutboxService {
    // Metodo para guardar eventos de ordenes creadas
    void saveOrderPlacedEvent(OrderPlacedEvent event, boolean isProcessed);

    // Obtener eventos pendientes
    List<OutboxEvent> getPendingEvents();

    // Marcar eventos como ya procesados
    void MarkAsProcessed(Long id);

}
