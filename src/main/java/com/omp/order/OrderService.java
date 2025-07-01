package com.omp.order;

import com.omp.cart.CartRepository;
import com.omp.delivery.DeliveryService;
import com.omp.delivery.dto.CreateDeliveryEvent;
import com.omp.order.dto.CreateOrderRequest;
import com.omp.orderMenu.OrderMenuService;
import com.omp.shop.ShopRepository;
import com.omp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
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
    private final ApplicationEventPublisher eventPublisher;

    public Order findOrderBy(final Long id) {
        return orderRepository.findById(id).orElseThrow();
    }

    public Long saveOrderBy(final CreateOrderRequest request) {
        Long ordererId = request.getOrdererId();
//        userRepository.findById(ordererId)
//                .ifPresentOrElse(u -> {
//                    if (u.isBan()) {
//                        throw new IllegalStateException();
//                    }
//                }, IllegalStateException::new);
        Long shopId = request.getShopId();
//        shopRepository.findById(shopId)
//                .ifPresentOrElse(s -> {
//                    if (!s.isOpen()) {
//                        throw new IllegalStateException();
//                    }
//                }, IllegalStateException::new);
//
        Long cartId = request.getCartId();
//        cartRepository.findById(cartId)
//                .ifPresentOrElse(c -> {
////                    if (!c.validateUserAndShop(ordererId, shopId)) {
////                        throw new IllegalStateException();
////                    }
//                }, IllegalStateException::new);

        OrderValidateDto validDto = orderRepository.validateOrder(ordererId, shopId, cartId);
        if (!validDto.isValid()) {
            throw new IllegalStateException();
        }

        Order newOrder = orderRepository.save(CreateOrderRequest.from(request));
        /*
            todo : 배달 생성 로직 분리 -> 배달은 다른 로직도 필요? (ex. 배달 기사 배정)
                   그럼 order에 delivery는 언제 설정? (그냥 delivery에 orderId를 넣을까?)
        */
//        eventPublisher.publishEvent(request);
//        eventPublisher.publishEvent(new CreateDeliveryEvent(newOrder.getId()));
//        Long deliveryId = deliveryService.saveDeliveryBy(new CreateDeliveryEvent(newOrder.getId()));
//        newOrder.setDeliveryId(deliveryId);

        return newOrder.getId();
    }
}
