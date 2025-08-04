package com.omp.order;

import com.omp.cart.CartRepository;
import com.omp.delivery.DeliveryRepository;
import com.omp.delivery.DeliveryService;
import com.omp.delivery.dto.CreateAsyncOrderEvent;
import com.omp.order.async.AsyncOrderManager;
import com.omp.order.async.OrderIdentifier;
import com.omp.order.async.OrderJobState;
import com.omp.order.async.OrderProcessingContext;
import com.omp.order.dto.CreateOrderRequest;
import com.omp.order.dto.OrderValidateDto;
import com.omp.orderMenu.OrderMenuService;
import com.omp.shop.ShopRepository;
import com.omp.user.UserRepository;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final ShopRepository shopRepository;
    private final OrderMenuService orderMenuService;
    private final DeliveryService deliveryService;
    private final DeliveryRepository deliveryRepository;
    private final AsyncOrderManager asyncOrderManager;
    private final ApplicationEventPublisher eventPublisher;
    private final Executor insertTaskExecutor;

    public Order findOrderBy(final Long id) {
        return orderRepository.findById(id).orElseThrow();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long saveOrderBy(final CreateOrderRequest request) {
        Long ordererId = request.getOrdererId();
        Long shopId = request.getShopId();
        Long cartId = request.getCartId();

        OrderValidateDto validDto = orderRepository.validateOrder(ordererId, shopId, cartId);
        if (!validDto.isValid()) {
            throw new IllegalStateException();
        }

        Order newOrder = orderRepository.save(CreateOrderRequest.from(request));

        return newOrder.getId();
    }

    @Transactional
    public String asyncOrder(CreateOrderRequest request) {
        Long ordererId = request.getOrdererId();
        if (!userRepository.existsById(ordererId)) {
            throw new IllegalArgumentException();
        }

        Long shopId = request.getShopId();
        if (!shopRepository.existsById(shopId)) {
            throw new IllegalArgumentException();
        }

        Long cartId = request.getCartId();
        if (!cartRepository.existsById(cartId)) {
            throw new IllegalArgumentException();
        }

        String uuid = UUID.randomUUID().toString();

        eventPublisher.publishEvent(new CreateAsyncOrderEvent(ordererId, cartId, shopId, uuid));
        asyncOrderManager.put(uuid, new OrderProcessingContext(new OrderIdentifier(ordererId, shopId, cartId)));

        return uuid;
    }

    public OrderJobState getOrderState(String uuid) {
        return asyncOrderManager.getJobState(uuid);
    }

    public OrderIdentifier getOrderIdentifier(String uuid) {
        return asyncOrderManager.getOrderIdentifier(uuid);
    }
}
