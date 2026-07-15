package com.ecommerce.order_service.scheduler;

import com.ecommerce.order_service.event.OrderPlacedEvent;
import com.ecommerce.order_service.model.OutboxEvent;
import com.ecommerce.order_service.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class MessageRelayer {

    private final RabbitTemplate rabbitTemplate;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    // Se ejecuta cada 10 segundos
    @Scheduled(fixedDelay = 10000)
    public void relayMessage() {

        // Obtenemos los eventos pendientes
        List<OutboxEvent> pendingEvents = outboxService.getPendingEvents();

        // Vemos si tenemos mensajes pendientes
        if(!pendingEvents.isEmpty()) {

            log.info("Relayer: Detectados {} mensajes pendientes.", pendingEvents.size());

            // Recorremos todos los eventos y los enviamos a RabbitMQ
            for(OutboxEvent event: pendingEvents) {

                try {
                    // Obtenemos el evento original mapeandolo
                    OrderPlacedEvent originalEvent = objectMapper.readValue(
                            event.getPayload(), OrderPlacedEvent.class
                    );

                    // Enviamos a RabbitMQ y los marcamos como procesados
                    rabbitTemplate.convertAndSend("order-events","order.placed", originalEvent);
                    outboxService.MarkAsProcessed(event.getId());
                    log.info("Mensaje recuperado y enviado: {}", event.getAggregateId());

                } catch (JacksonException e) {
                    log.error("Error deserializando evento {}: {}", event.getAggregateId(), e.getMessage());

                } catch (AmqpException e) {
                    log.error("Fallo el reintento para {}: {}",event.getAggregateId(), e.getMessage());
                }

            }
        }

    }

}
