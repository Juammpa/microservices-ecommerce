package com.ecommerce.order_service.mapper;

import com.ecommerce.order_service.dto.OrderLineItemsRequestDto;
import com.ecommerce.order_service.dto.OrderLineItemsResponseDto;
import com.ecommerce.order_service.dto.OrderRequestDto;
import com.ecommerce.order_service.dto.OrderResponseDto;
import com.ecommerce.order_service.model.Order;
import com.ecommerce.order_service.model.OrderLineItems;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    // 1. De Request a Entidad
    Order toOrder(OrderRequestDto orderRequest);

    // Metodo auxiliar (MapStruct lo usa automáticamente para convertir cada ítem de la lista)
    // Aquí NO hace falta @Mapping porque los campos (sku, price, quantity) se llaman igual.
    OrderLineItems toOrderLineItems(OrderLineItemsRequestDto orderLineItemsRequest);


    // 2. De Entidad a Response
    OrderResponseDto toOrderResponse(Order order);

    // Metodo auxiliar para la respuesta
    // Aquí NO hace falta @Mapping porque los campos (id, sku, price, quantity) se llaman igual.
    OrderLineItemsResponseDto toOrderLineItemsResponse(OrderLineItems orderLineItems);
}

