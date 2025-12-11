package com.food.webflux_redis_demo.services;

import com.food.webflux_redis_demo.api.dto.OrderDtos.CancelOrderResponse;
import com.food.webflux_redis_demo.api.dto.OrderDtos.CreateOrderRequest;
import com.food.webflux_redis_demo.api.dto.OrderDtos.OrderResponse;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {

    private static final String ORDER_KEY_PREFIX = "order:";
    private static final String ORDER_QUEUE_KEY = "order:queue";

    private final ReactiveStringRedisTemplate redisTemplate;

    public OrderService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<OrderResponse> createOrder(CreateOrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        String key = ORDER_KEY_PREFIX + orderId;

        Map<String, String> fields = Map.of(
                "orderId", orderId,
                "personId", request.personId(),
                "item", request.item(),
                "status", "PENDING"
        );

        // 1) Save order as hash
        // 2) Push a job message into a Redis list (simulating a task queue)
        return redisTemplate.opsForHash().putAll(key, fields)
                .then(
                        redisTemplate.opsForList()
                                .leftPush(ORDER_QUEUE_KEY, "NEW:" + orderId)
                )
                .then(getOrder(orderId));
    }

    public Mono<OrderResponse> getOrder(String orderId) {
        String key = ORDER_KEY_PREFIX + orderId;

        return redisTemplate.opsForHash().entries(key)
                .collectMap(e -> e.getKey().toString(), e -> e.getValue().toString())
                .flatMap(map -> {
                    if (map.isEmpty()) {
                        return Mono.empty();
                    }
                    return Mono.just(new OrderResponse(
                            map.get("orderId"),
                            map.get("personId"),
                            map.get("item"),
                            map.get("status")
                    ));
                });
    }

    public Mono<CancelOrderResponse> cancelOrder(String orderId) {
        String key = ORDER_KEY_PREFIX + orderId;

        // Update status in Redis, and push a cancellation task
        return redisTemplate.opsForHash().hasKey(key, "orderId")
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.empty();
                    }
                    return redisTemplate.opsForHash()
                            .put(key, "status", "CANCELLED")
                            .then(redisTemplate.opsForList()
                                    .leftPush(ORDER_QUEUE_KEY, "CANCEL:" + orderId)
                            )
                            .then(Mono.just(new CancelOrderResponse(orderId, "CANCELLED")));
                });
    }
}
