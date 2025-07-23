package com.omp.delivery;

import com.omp.delivery.dto.CreateAsyncOrderEvent;
import com.omp.order.Order;
import com.omp.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class DeliveryService {
    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;

    public Delivery findDeliveryBy(final Long id) {
        return deliveryRepository.findById(id).orElseThrow();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    @Async
//    @TransactionalEventListener
//    @Transactional
    public void saveDeliveryBy(final CreateAsyncOrderEvent event) {
//        Order order = orderRepository.findById(event.getOrderId()).orElseThrow();
//        Long deliveryId = deliveryRepository.save(CreateAsyncOrderEvent.from(event)).getId();
//        order.setDeliveryId(deliveryId);
    }
}
