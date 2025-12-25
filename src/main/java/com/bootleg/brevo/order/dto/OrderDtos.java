package com.bootleg.brevo.order.dto;


public class OrderDtos {

  public record CreateOrderRequest(
    String personId,
    String item
  ) {
  }

  public record OrderResponse(
    String orderId,
    String personId,
    String item,
    String status
  ) {
  }

  public record CancelOrderResponse(
    String orderId,
    String status
  ) {
  }
}
