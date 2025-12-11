package com.food.webflux_redis_demo.api;

import com.food.webflux_redis_demo.api.dto.OrderDtos.CancelOrderResponse;
import com.food.webflux_redis_demo.api.dto.OrderDtos.CreateOrderRequest;
import com.food.webflux_redis_demo.api.dto.OrderDtos.OrderResponse;
import com.food.webflux_redis_demo.services.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // POST /orders
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    // POST /orders/{orderId}/cancel
    @PostMapping(path = "/{orderId}/cancel",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<CancelOrderResponse> cancelOrder(@PathVariable String orderId) {
        return orderService.cancelOrder(orderId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String orderId) {
            super("Order not found: " + orderId);
        }
    }

    @GetMapping("/{orderId}")
    public Mono<OrderResponse> getOrder(@PathVariable String orderId) {
        return orderService.getOrder(orderId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)));
    }

}
