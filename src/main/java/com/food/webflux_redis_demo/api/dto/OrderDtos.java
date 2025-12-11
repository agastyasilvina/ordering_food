package com.food.webflux_redis_demo.api.dto;


public class OrderDtos {

    public record CreateOrderRequest(
            String personId,
            String item
    ) {}

    public record OrderResponse(
            String orderId,
            String personId,
            String item,
            String status
    ) {}

    public record CancelOrderResponse(
            String orderId,
            String status
    ) {}
}
