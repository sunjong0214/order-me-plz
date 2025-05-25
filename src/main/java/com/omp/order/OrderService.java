package com.omp.order;

import com.omp.cart.CartRepository;
import com.omp.delivery.DeliveryService;
import com.omp.delivery.dto.CreateDeliveryDto;
import com.omp.order.dto.CreateOrderRequest;
import com.omp.orderMenu.OrderMenuService;
import com.omp.shop.ShopRepository;
import com.omp.user.UserRepository;
import lombok.RequiredArgsConstructor;
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

    public Order findOrderBy(final Long id) {
        return orderRepository.findById(id).orElseThrow();
    }

    public Long saveOrderBy(final CreateOrderRequest request) {
        Long ordererId = request.getOrdererId();
        userRepository.findById(ordererId)
                .ifPresentOrElse(u -> {
                    if (u.isBan()) {
                        throw new IllegalStateException();
                    }
                }, IllegalStateException::new);
        /*
            todo : cart를 db에 저장? 혹은 쓰레드로컬로 메모리? 어처피 휘발성이 강함 db로 저장해야되는 이유는?
                   성능 테스트 필요
         */
        Long shopId = request.getShopId();
        shopRepository.findById(shopId)
                .ifPresentOrElse(s -> {
                    if (!s.isOpen()) {
                        throw new IllegalStateException();
                    }
                }, IllegalStateException::new);

        Long cartId = request.getCartId();
        cartRepository.findById(cartId)
                .ifPresentOrElse(c -> {
                    if (!c.validateUserAndShop(ordererId, shopId)) {
                        throw new IllegalStateException();
                    }
                }, IllegalStateException::new);
        /*
            todo : 이 부분 좀 최적화 가능? (ex. 하나의 로직으로 cartId로 가져오면?)
                    orderMenu는 cart에 담길 때 이미 검증이 완료
                    그리고 사용자의 요청은 cart만을 보내기 때문에 굳이?
         */
//        List<OrderMenu> orderMenus = orderMenuService.findOrderMenusByCartId(request.getCartId());
//        orderMenuService.existsOrderMenus(orderMenus);

        Order newOrder = orderRepository.save(CreateOrderRequest.from(request));

        /*
            todo : 배달 생성 로직 분리 -> 배달은 다른 로직도 필요? (ex. 배달 기사 배정)
                    그럼 order에 delivery는 언제 설정? (그냥 delivery에 orderId를 넣을까?)
         */
        Long deliveryId = deliveryService.saveDeliveryBy(new CreateDeliveryDto(newOrder.getId()));
        newOrder.setDeliveryId(deliveryId);

        return newOrder.getId();
    }
}
