package com.ecommerce.order_service.repository;

import com.ecommerce.order_service.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    // Buscamos los eventos que aun no han sido enviados exitosamente
    List<OutboxEvent> findByProcessedFalse();

}
