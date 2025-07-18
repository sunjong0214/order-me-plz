package com.omp.delivery.dto;

import com.omp.order.Order;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
public class CreateAsyncOrderEvent {
    @Positive
    private final Long ordererId;
    @Positive
    private final Long cartId;
    @Positive
    private final Long shopId;
    @NotBlank
    private final String uuid;

    public static Order from (CreateAsyncOrderEvent event) {
        return new Order(event.ordererId, event.cartId, event.shopId);
    }
//    public static Delivery from(final CreateDeliveryEvent event) {
//        return new Delivery(event.orderId);
//    }
}
