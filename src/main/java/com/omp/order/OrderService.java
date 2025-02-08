package com.omp.order;

import com.omp.order.dto.CreateOrderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class OrderService {
    private final OrderRepository orderRepository;

    public Order findOrderBy(final Long id) {
        return orderRepository.findById(id).orElseThrow();
    }

    public Long saveOrderBy(final CreateOrderDto createOrderDto) {
        return orderRepository.save(CreateOrderDto.from(createOrderDto)).getId();
    }
}
