package com.bootleg.brevo.workers;

import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class OrderWorker {

  private static final String ORDER_QUEUE_KEY = "order:queue";
  private static final String ORDER_KEY_PREFIX = "order:";

  private final ReactiveStringRedisTemplate redisTemplate;

  public OrderWorker(ReactiveStringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @PostConstruct
  public void startWorker() {
    // Poll Redis every 200ms for new jobs
    Flux.interval(Duration.ofMillis(200))
      .flatMap(tick ->
        redisTemplate.opsForList()
          .rightPop(ORDER_QUEUE_KEY) // get oldest job
      )
      .flatMap(this::handleMessage)
      .onErrorContinue((ex, value) -> {
        // log error in real code
        ex.printStackTrace();
      })
      .subscribe(); // start the stream
  }

  private Mono<Void> handleMessage(String msg) {
    if (msg == null) {
      return Mono.empty();
    }

    String[] parts = msg.split(":", 2);
    String type = parts[0];
    String orderId = parts.length > 1 ? parts[1] : null;

    if (orderId == null) {
      return Mono.empty();
    }

    return switch (type) {
      case "NEW" -> processNewOrder(orderId);
      case "CANCEL" -> processCancel(orderId);
      default -> Mono.empty();
    };
  }

  private Mono<Void> processNewOrder(String orderId) {
    // Simulate 2s processing, then mark order as COMPLETED
    return Mono.delay(Duration.ofSeconds(2))
      .then(
        redisTemplate.opsForHash()
          .put(ORDER_KEY_PREFIX + orderId, "status", "COMPLETED")
      )
      .then();
  }

  private Mono<Void> processCancel(String orderId) {
    // for now, nothing extra; you could add extra logic here
    return Mono.empty();
  }
}
