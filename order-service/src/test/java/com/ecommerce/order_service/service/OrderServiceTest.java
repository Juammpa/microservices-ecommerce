package com.ecommerce.order_service.service;

import com.ecommerce.order_service.dto.OrderLineItemsRequestDto;
import com.ecommerce.order_service.dto.OrderRequestDto;
import com.ecommerce.order_service.dto.OrderResponseDto;
import com.ecommerce.order_service.event.OrderPlacedEvent;
import com.ecommerce.order_service.exception.ResourceNotFoundException;
import com.ecommerce.order_service.mapper.OrderMapper;
import com.ecommerce.order_service.model.Order;
import com.ecommerce.order_service.model.OrderLineItems;
import com.ecommerce.order_service.model.OrderStatus;
import com.ecommerce.order_service.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService - Unit Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private OrderService orderService;

    // =========== Datos de pruebas reutilizables ===========
    private OrderRequestDto orderRequest;
    private Order mappedOrder;
    private Order savedOrder;
    private OrderResponseDto orderResponseDto;
    private static final String USER_ID = "user-123";
    private static final String ORDER_NUMBER = "order-abc-456";

    @BeforeEach
    void setUp() {
        // Habilitamos el servicio por defecto (simula ordersEEnabled = true)
        ReflectionTestUtils.setField(orderService, "ordersEnabled", true);

        OrderLineItemsRequestDto itemsRequest = new OrderLineItemsRequestDto("SKU-001", new BigDecimal("99.99"),2);
        orderRequest = new OrderRequestDto(List.of(itemsRequest), "user@email.com");

        OrderLineItems lineItem = OrderLineItems.builder()
                .id(1L)
                .sku("SKU-001")
                .price(new BigDecimal("99.99"))
                .quantity(2)
                .build();

        // Orden que retorna el mapper (antes de persistir)
        mappedOrder = Order.builder()
                .orderLineItemsList(List.of(lineItem))
                .build();

        // Orden que retornna el repositorio (despues de persistir, ccn ID asignado)
        savedOrder = Order.builder()
                .id(1L)
                .orderNumber(ORDER_NUMBER)
                .userId(USER_ID)
                .status(OrderStatus.PLACED)
                .orderLineItemsList(List.of(lineItem))
                .build();

        orderResponseDto = OrderResponseDto.builder()
                .id(1L)
                .orderNumber(ORDER_NUMBER)
                .status(OrderStatus.PLACED)
                .build();
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("placeOrder()")
    class PlaceOrderTests {

        @Test
        @DisplayName("Debe guardar la orden y publiccar evento en RabbitMQ exitosamente")
        void placeOrder_HappyPath_SavesOrderAndPublishesEvent() {
            // --- Arrange ---
            when(orderMapper.toOrder(orderRequest)).thenReturn(mappedOrder);
            when(orderRepository.save(mappedOrder)).thenReturn(savedOrder);
            when(orderMapper.toOrderResponse(savedOrder)).thenReturn(orderResponseDto);

            // --- Act ---
            OrderResponseDto result = orderService.placeOrder(orderRequest, USER_ID);

            // --- Assert ---
            assertThat(result).isNotNull();
            assertThat(result.getOrderNumber()).isEqualTo(ORDER_NUMBER);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PLACED);

            // Verificamos que se asignaron los campos correctos antes de guardar
            assertThat(mappedOrder.getUserId()).isEqualTo(USER_ID);
            assertThat(mappedOrder.getStatus()).isEqualTo(OrderStatus.PLACED);
            assertThat(mappedOrder.getOrderNumber()).isNotNull();

            // Verificamos que el evento llega a RabbitMQ con el exchange y routing key correctos
            verify(rabbitTemplate).convertAndSend(eq("order-events"),eq("order.placed"), any(OrderPlacedEvent.class));

            // Verificamos  que el Outbox se guardo con processed=true (RabbitMQ funciono)
            ArgumentCaptor<Boolean> processedCaptor = ArgumentCaptor.forClass(Boolean.class);
            verify(outboxService).saveOrderPlacedEvent(any(OrderPlacedEvent.class), processedCaptor.capture());
            assertThat(processedCaptor.getValue()).isTrue();

        }

        @Test
        @DisplayName("Debe guardar en Outbox con processed=false cuando RabbitMQ falla")
        void placeOrder_WhenRabbitMQFails_SavesOutboxWithProcessedFalse(){
            // --- Arrange ---
            when(orderMapper.toOrder(orderRequest)).thenReturn(mappedOrder);
            when(orderRepository.save(mappedOrder)).thenReturn(savedOrder);
            when(orderMapper.toOrderResponse(savedOrder)).thenReturn(orderResponseDto);

            // Simulamos que RabbitMQQ lanza una excepcion al enviar
            doThrow(new AmqpException("Connection refused"))
                    .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(OrderPlacedEvent.class));

            // --- Act ---
            // El servicio no debe explotar: captura la AmqpException internamente
            OrderResponseDto result = orderService.placeOrder(orderRequest, USER_ID);

            // --- Assert ---
            assertThat(result).isNotNull();

            // El Outbox debe guardar con processed=false para reintento posterior
            ArgumentCaptor<Boolean> processedCaptor = ArgumentCaptor.forClass(Boolean.class);
            verify(outboxService).saveOrderPlacedEvent(any(OrderPlacedEvent.class), processedCaptor.capture());
            assertThat(processedCaptor.getValue()).isFalse();

        }

        @Test
        @DisplayName("Debe lanzar excepcion cuando el servicio esta deshabilitado por configuracion")
        void placeOrder_WhenServiceDisabled_ThrowsRuntimeException() {
            // --- Arrange ---
            ReflectionTestUtils.setField(orderService,"ordersEnabled", false);

            // --- Act & Assert ---
            assertThatThrownBy(() -> orderService.placeOrder(orderRequest, USER_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("mantenimiento");

            // Ninguna dependencia debe ser invocada si el servicio esta deshabilitado
            verifyNoInteractions(orderRepository, rabbitTemplate, outboxService);
        }

        @Test
        @DisplayName("Debe asignaa un orderNumber unico (UUID) a cada orden")
        void placedOrder_ShouldAssignUniqueOrderNumber() {
            // --- Arrange ---
            Order secondMappedOrder = Order.builder()
                    .orderLineItemsList(mappedOrder.getOrderLineItemsList())
                    .build();

            when(orderMapper.toOrder(orderRequest))
                    .thenReturn(mappedOrder)
                    .thenReturn(secondMappedOrder);
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(orderMapper.toOrderResponse(any())).thenReturn(orderResponseDto);

            // --- Act ---
            orderService.placeOrder(orderRequest, USER_ID);
            orderService.placeOrder(orderRequest, USER_ID);

            // --- Assert ---
            // Cada orden debe recibir un UUID distinto
            assertThat(mappedOrder.getOrderNumber())
                    .isNotEqualTo(secondMappedOrder.getOrderNumber());
        }

    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getOrders()")
    class GetOrdersTests {

        @Test
        @DisplayName("Amin debe recibir todas las ordenes del sistema")
        void getOrders_whenAdmin_ReturnsAllOrders() {
            // --- Arrange ---
            when(orderRepository.findAll()).thenReturn(List.of(savedOrder));
            when(orderMapper.toOrderResponse(savedOrder)).thenReturn(orderResponseDto);

            // --- Act ---
            List<OrderResponseDto> result = orderService.getOrders(USER_ID, true);

            // --- Asert ---
            assertThat(result).hasSize(1);
            verify(orderRepository).findAll();

            // Un admin nunca debe ver solo sus propias ordenes
            verify(orderRepository, never()).findByUserId(any());

        }

        @Test
        @DisplayName("Usuario regular solo debe recibir sus propias ordenes.")
        void getOrders_whenRegularUser_ReturnsOnlyUserOrders() {
            // --- Arrange ---
            when(orderRepository.findByUserId(USER_ID)).thenReturn(List.of(savedOrder));
            when(orderMapper.toOrderResponse(savedOrder)).thenReturn(orderResponseDto);

            // --- Act ---
            List<OrderResponseDto> result = orderService.getOrders(USER_ID, false);

            // --- Assert ---
            assertThat(result).hasSize(1);
            verify(orderRepository).findByUserId(USER_ID);
            verify(orderRepository, never()).findAll();
        }

        @Test
        @DisplayName("Debe retornar lista vacia cuando el usuario no tiene ordenes")
        void getOrders_WhenUserHasNoOrders_ReturnsEmptyList() {
            // --- Arrange ---
            when(orderRepository.findByUserId(USER_ID)).thenReturn(List.of());

            // --- Act ---
            List<OrderResponseDto> result = orderService.getOrders(USER_ID, false);

            // --- Assert ---
            assertThat(result).isEmpty();
        }

    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getOrderById()")
    class GetOrderByIdTests {

        @Test
        @DisplayName("Debe retornar la orden cuando el ID existe")
        void getOrderById_WhenExists_ReturnsOrder() {
            // --- Arrange ---
            when(orderRepository.findById(1L)).thenReturn(Optional.of(savedOrder));
            when(orderMapper.toOrderResponse(savedOrder)).thenReturn(orderResponseDto);

            // --- Act ---
            OrderResponseDto result = orderService.getOrderById(1L);

            // --- Assert ---
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Debe lanzar excepcion cuando el ID no existe")
        void getOrderById_WhenNotFound_ThrowsResourceNotFoundException() {
            // --- Arrange ---
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            // --- Act & Assert ---
            assertThatThrownBy(() -> orderService.getOrderById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("deleteOrder()")
    class DeleteOrderTests {

        @Test
        @DisplayName("Debe eliminar la orden cuando el ID existe")
        void deleteOrder_WhenExists_DeletesSuccessfully() {
            // --- Arrange ---
            when(orderRepository.existsById(1L)).thenReturn(true);

            // --- Act ---
            orderService.deleteOrder(1L);

            // --- Assert ---
            // Verificamos que deleteById fue llamado exactamente una vez con el ID correcto
            verify(orderRepository, times(1)).deleteById(1L);
        }

        @Test
        @DisplayName("Debe lanzar excepcion cuando el ID no existe")
        void deleteOrder_WhenNotFound_ThrowsResourceNotFoundException() {
            // --- Arrange ---
            when(orderRepository.existsById(99L)).thenReturn(false);

            // --- Act & Assert ---
            assertThatThrownBy(() -> orderService.deleteOrder(99L))
                    .isInstanceOf(ResourceNotFoundException.class);

            // Si la orden no existe, no debe intentar eliminar nada
            verify(orderRepository, never()).deleteById(any());
        }

    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatusTests {

        @Test
        @DisplayName("Debe actualizar el estado cuando el orderNumber existe")
        void updateStatus_WhenOrderExists_UpdatesStatus() {
            // --- Arrange ---
            when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(savedOrder));

            // --- Act ---
            orderService.updateStatus(ORDER_NUMBER, OrderStatus.CONFIRMED);

            // --- Assert ---
            // Verificamos que el estado se actualizo en el objeto antes de guardarlo
            assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(orderRepository).save(savedOrder);
        }

        @Test
        @DisplayName("No debe lanzar excepcion cuando el orderNumber no existe (log silencioso)")
        void updateStatus_WhenOrderNotFound_DoesNotThrow() {
            // --- Arrange ---
            when(orderRepository.findByOrderNumber("orden-inexistente")).thenReturn(Optional.empty());

            // --- Act & Assert ---
            // El servicio usa ifPresentOrElse con log en el else: no debe explotar
            assertThatCode(() -> orderService.updateStatus("orden-inexistente", OrderStatus.CANCELLED))
                    .doesNotThrowAnyException();

            // Si no se encontro la orden, no debe intentar guardar nada
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Debe poder cancelar una orden existente")
        void updateStatus_ToCancelled_UpdatesCorrectly() {
            // --- Arrange ---
            when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(savedOrder));

            // --- Act ---
            orderService.updateStatus(ORDER_NUMBER, OrderStatus.CANCELLED);

            // --- Assert ---
            assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(orderRepository).save(savedOrder);
        }


    }


}