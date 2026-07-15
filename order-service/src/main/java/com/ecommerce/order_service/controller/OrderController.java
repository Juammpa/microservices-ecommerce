package com.ecommerce.order_service.controller;

import com.ecommerce.order_service.dto.OrderRequestDto;
import com.ecommerce.order_service.dto.OrderResponseDto;
import com.ecommerce.order_service.model.OrderStatus;
import com.ecommerce.order_service.service.IOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final IOrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponseDto placeOrder(@Valid @RequestBody OrderRequestDto orderRequest,
                                                          @AuthenticationPrincipal Jwt jwt)
    {
        return orderService.placeOrder(orderRequest, jwt.getSubject());
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<OrderResponseDto> getOrders(@AuthenticationPrincipal Jwt jwt) {

        // Obtener lista de roles
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        boolean isAdmin = false;

        // Vemos si tiene el rol ADMIN
        if(realmAccess!=null && realmAccess.containsKey("roles")) {
            List<String> roles = (List<String>) realmAccess.get("roles");

            isAdmin = roles.stream().anyMatch(role -> role.equals("ADMIN"));

        }

        return orderService.getOrders(jwt.getSubject(), isAdmin);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public OrderResponseDto getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
    }


}


