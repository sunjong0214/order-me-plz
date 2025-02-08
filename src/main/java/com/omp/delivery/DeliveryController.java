package com.omp.delivery;

import com.omp.delivery.dto.CreateDeliveryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/delivery")
public class DeliveryController {
    private final DeliveryService deliveryService;

    @GetMapping("/{id}")
    public Delivery getDelivery(final @PathVariable Long id) {
        return deliveryService.findDeliveryBy(id);
    }

    @PostMapping
    public Long createDelivery(final @RequestBody CreateDeliveryRequest request) {
        return deliveryService.saveDeliveryBy(CreateDeliveryRequest.from(request));
    }
}
