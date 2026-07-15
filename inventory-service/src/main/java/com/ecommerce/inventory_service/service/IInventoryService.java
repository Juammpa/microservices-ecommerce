package com.ecommerce.inventory_service.service;

import com.ecommerce.inventory_service.dto.InventoryRequestDto;
import com.ecommerce.inventory_service.dto.InventoryResponseDto;

import java.util.List;

public interface IInventoryService {

    boolean isInStock(String sku, Integer quantity);
    InventoryResponseDto createInventory(InventoryRequestDto inventoryRequest);
    List<InventoryResponseDto> getAllInventory();
    InventoryResponseDto updateInventory(Long id, InventoryRequestDto inventoryRequest);
    void deleteInventory(Long id);

    // Comunicacion con order-service
    void reduceStock(String sku, Integer quantity);
}


