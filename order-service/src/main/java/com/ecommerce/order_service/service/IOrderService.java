package com.ecommerce.order_service.service;

import com.ecommerce.order_service.dto.OrderRequestDto;
import com.ecommerce.order_service.dto.OrderResponseDto;
import com.ecommerce.order_service.model.OrderStatus;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IOrderService {

    OrderResponseDto placeOrder(OrderRequestDto orderRequest, String userId); // Create
    List<OrderResponseDto> getOrders(String userId, boolean isAdmin);  // Read All by User
    OrderResponseDto getOrderById(Long id);                 // Read One
    void deleteOrder(Long id);                           // Delete
    void updateStatus(String orderNumber ,OrderStatus newStatus);  // Update status
}


