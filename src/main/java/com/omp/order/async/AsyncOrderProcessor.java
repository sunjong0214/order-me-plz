package com.omp.order.async;

import com.omp.cart.CartRepository;
import com.omp.delivery.dto.CreateAsyncOrderEvent;
import com.omp.order.OrderRepository;
import com.omp.shop.ShopRepository;
import com.omp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class AsyncOrderProcessor {
    private final OrderRepository orderRepository;
    private final ShopRepository shopRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long processOrderTask(CreateAsyncOrderEvent event) {
        Long ordererId = event.getOrdererId();
        validateOrder(ordererId);

        Long shopId = event.getShopId();
        validateShop(shopId);

        Long cartId = event.getCartId();
//            validateCart(cartId, ordererId, shopId);

        return orderRepository.save(CreateAsyncOrderEvent.from(event)).getId();
    }

    private void validateCart(Long cartId, Long ordererId, Long shopId) {
        cartRepository.findById(cartId)
                .ifPresentOrElse(c -> {
                    if (!c.validateUserAndShop(ordererId, shopId)) {
                        throw new IllegalStateException();
                    }
                }, IllegalStateException::new);
    }

    private void validateShop(Long shopId) {
        shopRepository.findById(shopId)
                .ifPresentOrElse(s -> {
                    if (!s.isOpen()) {
                        throw new IllegalStateException();
                    }
                }, IllegalStateException::new);
    }

    private void validateOrder(Long ordererId) {
        userRepository.findById(ordererId)
                .ifPresentOrElse(u -> {
                    if (u.isBan()) {
                        throw new IllegalStateException();
                    }
                }, IllegalStateException::new);
    }
}
