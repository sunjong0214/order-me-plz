package com.omp.delivery.dto;

import com.omp.delivery.Delivery;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
public class CreateDeliveryEvent {
//    @Positive
//    private final Long ordererId;
//    @Positive
//    private final Long cartId;
//    @Positive
//    private final Long shopId;
    @Positive
    private final Long orderId;

    public static Delivery from(final CreateDeliveryEvent event) {
        return new Delivery(event.orderId);
    }
}
