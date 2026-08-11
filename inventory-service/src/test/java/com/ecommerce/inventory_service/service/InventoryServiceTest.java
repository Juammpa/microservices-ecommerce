package com.ecommerce.inventory_service.service;

import com.ecommerce.inventory_service.dto.InventoryRequestDto;
import com.ecommerce.inventory_service.dto.InventoryResponseDto;
import com.ecommerce.inventory_service.exception.ResourceNotFoundException;
import com.ecommerce.inventory_service.mapper.InventoryMapper;
import com.ecommerce.inventory_service.model.Inventory;
import com.ecommerce.inventory_service.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService - Unit Tests")
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryMapper inventoryMapper;

    @InjectMocks
    private InventoryService inventoryService;

    // ========= Datos de prueba reutilizables ==========
    private InventoryRequestDto inventoryRequest;
    private Inventory mappedInventory;
    private Inventory savedInventory;
    private InventoryResponseDto inventoryResponse;

    private static final String SKU = "SKU-001";
    private static final Long INVENTORY_ID = 1L;

    @BeforeEach
    void setUp() {
        // Desahabiltamos backorders por defecto (comportamiento normal de produccion)
        ReflectionTestUtils.setField(inventoryService, "allowBackOrders", false);

        inventoryRequest = new InventoryRequestDto(SKU, 10);

        // Inventory que retorna el mapper a partir del request (sin ID aun)
        mappedInventory = Inventory.builder()
                .sku(SKU)
                .quantity(10)
                .build();

        // Inventory que retorna el repositorio luego de persistir (con ID asignado)
        savedInventory = Inventory.builder()
                .id(INVENTORY_ID)
                .sku(SKU)
                .quantity(10)
                .build();

        inventoryResponse = InventoryResponseDto.builder()
                .id(INVENTORY_ID)
                .sku(SKU)
                .quantity(10)
                .inStock(true)
                .build();
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("isInStock()")
    class IsInStockTests {

        @Test
        @DisplayName("Debe retornar true cuando hay stock suficiente")
        void isInStock_WhenSufficientStock_ReturnsTrue() {
            // --- Arrange ---
            when(inventoryRepository.findBySku(SKU)).thenReturn(Optional.of(savedInventory));

            // --- Act ---
            boolean result = inventoryService.isInStock(SKU, 5);

            // --- Assert ---
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Debe retornar true cuando la cantidad pedida es exactamente igual al stock")
        void isInStock_WhenStockEqualsRequestedQuantity_ReturnsTrue() {
            // --- Arrange ---
            // Caso limite: pedimos exactamente lo que hay (10 == 10)
            when(inventoryRepository.findBySku(SKU)).thenReturn(Optional.of(savedInventory));

            // --- Act ---
            boolean result =  inventoryService.isInStock(SKU, 10);

            // --- Assert ---
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Debe retornar false cuando el stock es insuficiente")
        void isInStock_WhenInsufficientStock_ReturnsFalse() {
            // --- Arrange ---
            // El inventario tiene 10 unidades, pero pedimos 20
            when(inventoryRepository.findBySku(SKU)).thenReturn(Optional.of(savedInventory));

            // --- Act ---
            boolean result = inventoryService.isInStock(SKU, 20);

            // --- Assert ---
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Debe retornar false cuando el SKU no existe en el inventario")
        void isInStock_WhenSkuNotFound_ReturnsFalse() {
            // --- Arrange ---
            when(inventoryRepository.findBySku("SKU-INEXISTENTE")).thenReturn(Optional.empty());

            // --- Act ---
            boolean result = inventoryService.isInStock("SKU-INEXISTENTE", 1);

            // --- Assert ---
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Debe retornar true sin consultar la BD cuando el modo backorder esta activo")
        void isInStock_WhenBackOrderEnabled_ReturnsTrueWithoutDbCall() {
            // --- Arrange ---
            ReflectionTestUtils.setField(inventoryService, "allowBackOrders", true);

            // --- Act ---
            boolean result =  inventoryService.isInStock(SKU, 999);

            // --- Assert ---
            assertThat(result).isTrue();

            // Con backorder activo, nunca debe consultar la base de datos
            verifyNoInteractions(inventoryRepository);
        }

    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("createInventory()")
    class CreateInventoryTests {

        @Test
        @DisplayName("Debe crear el inventario correctamente cuando el SKU no existe")
        void createInventory_WhenSkuIsNew_CreatesSuccessfully() {
            // --- Arrange ---
            when(inventoryRepository.existsBySku(SKU)).thenReturn(false);
            when(inventoryMapper.toModel(inventoryRequest)).thenReturn(mappedInventory);
            when(inventoryRepository.save(mappedInventory)).thenReturn(savedInventory);
            when(inventoryMapper.toResponse(savedInventory)).thenReturn(inventoryResponse);

            // --- Act --
            InventoryResponseDto result = inventoryService.createInventory(inventoryRequest);

            // --- Assert ---
            assertThat(result).isNotNull();
            assertThat(result.getSku()).isEqualTo(SKU);
            assertThat(result.getQuantity()).isEqualTo(10);
            assertThat(result.isInStock()).isTrue();
        }

        @Test
        @DisplayName("Debe lanzar excepcion cuando el SKU ya existe (duplicado)")
        void createInventory_WhenSkuAlreadyExists_ThrowsRuntimeException() {
            // --- Arrange ---
            when(inventoryRepository.existsBySku(SKU)).thenReturn(true);

            // --- Act & Assert ---
            assertThatThrownBy(() -> inventoryService.createInventory(inventoryRequest))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(SKU);

            // Si el SKU ya existe, no debe intentar persistir nada
            verify(inventoryRepository, never()).save(any());
        }

    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getAllInventory()")
    class GetAllInventoryTests {

        @Test
        @DisplayName("Debe retornar todos los registros de inventario mapeados")
        void getAllInventory_WhenRecordsExist_ReturnsMappedList() {
            // --- Arrange ---
            Inventory secondInventory =  Inventory.builder()
                    .id(2L).sku("SKU-002").quantity(5).build();

            InventoryResponseDto secondResponse = InventoryResponseDto.builder()
                    .id(2L).sku("SKU-002").quantity(5).inStock(true).build();

            when(inventoryRepository.findAll()).thenReturn(List.of(savedInventory, secondInventory));
            when(inventoryMapper.toResponse(savedInventory)).thenReturn(inventoryResponse);
            when(inventoryMapper.toResponse(secondInventory)).thenReturn(secondResponse);

            // --- Act ---
            List<InventoryResponseDto> result = inventoryService.getAllInventory();

            // --- Assert ---
            assertThat(result).hasSize(2);
            assertThat(result).extracting(InventoryResponseDto::getSku)
                    .containsExactly(SKU, "SKU-002");
        }

        @Test
        @DisplayName("Debe retornar lista vacia cuando no hay registros")
        void getAllInventory_WhenNoRecords_ReturnsEmptyList() {
            // --- Arrange ---
            when(inventoryRepository.findAll()).thenReturn(List.of());

            // --- Act ---
            List<InventoryResponseDto> result = inventoryService.getAllInventory();

            // --- Assert ---
            assertThat(result).isEmpty();
            verify(inventoryMapper, never()).toResponse(any());

        }
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("updateInventory()")
    class UpdateInventoryTests {

        @Test
        @DisplayName("Debe actualizar SKU y cantidad correctamente cuando el ID existe")
        void updateInventory_WhenExists_UpdatesAndReturns() {
            // --- Arrange ---
            InventoryRequestDto updateRequest = new InventoryRequestDto("SKU-001-UPDATED", 50);

            InventoryResponseDto updatedResponse = InventoryResponseDto.builder()
                    .id(INVENTORY_ID).sku("SKU-001-UPDATED").quantity(50).inStock(true).build();

            when(inventoryRepository.findById(INVENTORY_ID)).thenReturn(Optional.of(savedInventory));
            when(inventoryRepository.save(savedInventory)).thenReturn(savedInventory);
            when(inventoryMapper.toResponse(savedInventory)).thenReturn(updatedResponse);

            // --- Act ---
            InventoryResponseDto result = inventoryService.updateInventory(INVENTORY_ID, updateRequest);

            // --- Assert ---
            assertThat(result.getSku()).isEqualTo("SKU-001-UPDATED");
            assertThat(result.getQuantity()).isEqualTo(50);

            // Verificamos que los campos se sobreescribieron en el objeto antes de guardarlo
            assertThat(savedInventory.getSku()).isEqualTo("SKU-001-UPDATED");
            assertThat(savedInventory.getQuantity()).isEqualTo(50);

            verify(inventoryRepository).save(savedInventory);
        }

        @Test
        @DisplayName("Debe lanzar excepcion cuando el ID no existe")
        void updateInventory_WhenNotFound_ThrowsResourceNotFoundException() {
            // --- Arrange ---
            when(inventoryRepository.findById(99L)).thenReturn(Optional.empty());

            // --- Act & Assert ---
            assertThatThrownBy(() -> inventoryService.updateInventory(99L, inventoryRequest))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(inventoryRepository, never()).save(any());
        }

    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("deleteInventory()")
    class DeleteInventoryTests {

        @Test
        @DisplayName("Debe eliminar el inventario cuando el ID existe")
        void deleteInventory_WhenExists_DeletesSuccessfully() {
            // --- Arrange ---
            when(inventoryRepository.existsById(INVENTORY_ID)).thenReturn(true);

            // --- Act ---
            inventoryService.deleteInventory(INVENTORY_ID);

            // --- Assert ---
            verify(inventoryRepository, times(1)).deleteById(INVENTORY_ID);

        }

        @Test
        @DisplayName("Debe lanzar excepcion cuando el ID no existe")
        void deleteInventory_WhenNotFound_ThrowsResourceNotFoundException() {
            // --- Arrange ---
            when(inventoryRepository.existsById(99L)).thenReturn(false);

            // --- Act & Assert ---
            assertThatThrownBy(() -> inventoryService.deleteInventory(99L))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(inventoryRepository, never()).deleteById(any());
        }

    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("reduceStock()")
    class ReduceStockTests {

        @Test
        @DisplayName("Debe reducir el stock correctamente cuando hay suficiente cantidad")
        void reduceStock_WhenSufficientStock_ReducesQuantity() {
            // --- Arrange ---
            // savedInventory tiene 10 unidades, reducimos 3
            when(inventoryRepository.findBySku(SKU)).thenReturn(Optional.of(savedInventory));

            // --- Act ---
            inventoryService.reduceStock(SKU, 3);

            // --- Assert ---
            // El stock debe quedar en 7
            assertThat(savedInventory.getQuantity()).isEqualTo(7);
            verify(inventoryRepository).save(savedInventory);
        }

        @Test
        @DisplayName("Debe reducir el stock a cero cuando se consume todo el inventario")
        void reduceStock_WhenConsumingAllStock_LeavesZeroQuantity() {
            // --- Arrange --
            // Caso limite: consumimos exactamente todoo el stock disponible
            when(inventoryRepository.findBySku(SKU)).thenReturn(Optional.of(savedInventory));

            // --- Act ---
            inventoryService.reduceStock(SKU, 10);

            // --- Assert ---
            assertThat(savedInventory.getQuantity()).isEqualTo(0);
            verify(inventoryRepository).save(savedInventory);
        }

        @Test
        @DisplayName("Debe lanzar excepcion cuando el stock es insuficiente")
        void reduceStock_WhenInsufficientStock_ThrowsRuntimeException() {
            // --- Arrange ---
            when(inventoryRepository.findBySku(SKU)).thenReturn(Optional.of(savedInventory));

            // --- Act & Assert ---
            assertThatThrownBy(() -> inventoryService.reduceStock(SKU, 20))
                    .isInstanceOf(RuntimeException.class);

            // Si el stock es insuficiente, no debe persistir ningun cambio
            verify(inventoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Debe lanzar excepcion cuando el SKU no existe")
        void reduceStock_WhenSkuNotFound_ThrowsRuntimeException() {
            // --- Arrange ---
            when(inventoryRepository.findBySku("SKU-INEXISTENTE")).thenReturn(Optional.empty());

            // --- Act & Assert ---
            assertThatThrownBy(() -> inventoryService.reduceStock("SKU-INEXISTENTE",5))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("SKU-INEXISTENTE");

            verify(inventoryRepository, never()).save(any());
        }

    }

}