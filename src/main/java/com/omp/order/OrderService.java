package com.omp.order;

import com.omp.delivery.Delivery;
import com.omp.delivery.DeliveryRepository;
import com.omp.order.dto.CreateOrderDto;
import com.omp.shop.Shop;
import com.omp.shop.ShopRepository;
import com.omp.user.User;
import com.omp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final DeliveryRepository deliveryRepository;
    private final ShopRepository shopRepository;

    public Order findOrderBy(final Long id) {
        return orderRepository.findById(id).orElseThrow();
    }

    public Long saveOrderBy(final CreateOrderDto createOrderDto) {
        User orderer = userRepository.findById(createOrderDto.getOrdererId()).orElseThrow();
        Delivery delivery = deliveryRepository.findById(createOrderDto.getDeliveryId()).orElseThrow();
        Shop shop = shopRepository.findById(createOrderDto.getShopId()).orElseThrow();

        return orderRepository.save(CreateOrderDto.from(createOrderDto)).getId();
    }
}
