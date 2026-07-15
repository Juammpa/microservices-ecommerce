package com.ecommerce.order_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateId; // Guardaremos el orderNumber
    private String type;

    @Column(columnDefinition = "TEXT")
    private String payload;     // El objeto convertido a JSON String

    private LocalDateTime createdAt;
    private boolean processed;  // Estado para el futuro proceso de envio


}
