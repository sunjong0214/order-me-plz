package com.omp.delivery;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryService {
    private final DeliveryRepository deliveryRepository;

    public Delivery findDeliveryBy(final Long id) {
        return deliveryRepository.findById(id).orElseThrow();
    }

    public Long saveDeliveryBy(final CreateDeliveryDto createDeliveryDto) {
        return deliveryRepository.save(CreateDeliveryDto.of(createDeliveryDto)).getId();
    }
}
