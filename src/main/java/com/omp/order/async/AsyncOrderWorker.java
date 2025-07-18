package com.omp.order.async;

import com.omp.cart.CartRepository;
import com.omp.delivery.dto.CreateAsyncOrderEvent;
import com.omp.order.Order;
import com.omp.order.OrderRepository;
import com.omp.shop.ShopRepository;
import com.omp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
public class AsyncOrderWorker {
    private final OrderRepository orderRepository;
    private final ShopRepository shopRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final AsyncOrderManager asyncOrderManager;

    private static final Log log = LogFactory.getLog(AsyncOrderWorker.class);

    @TransactionalEventListener
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void asyncCreateOrder(CreateAsyncOrderEvent event) {
        // validation
        try {
            Long ordererId = event.getOrdererId();
            userRepository.findById(ordererId)
                    .ifPresentOrElse(u -> {
                        if (u.isBan()) {
                            throw new IllegalStateException();
                        }
                    }, IllegalStateException::new);

            Long shopId = event.getShopId();
            shopRepository.findById(shopId)
                    .ifPresentOrElse(s -> {
                        if (!s.isOpen()) {
                            throw new IllegalStateException();
                        }
                    }, IllegalStateException::new);

            Long cartId = event.getCartId();
            cartRepository.findById(cartId)
                    .ifPresentOrElse(c -> {
                        if (!c.validateUserAndShop(ordererId, shopId)) {
                            throw new IllegalStateException();
                        }
                    }, IllegalStateException::new);

//      insert
            Order newOrder = orderRepository.save(CreateAsyncOrderEvent.from(event));
            asyncOrderManager.complete(event.getUuid(), newOrder.getId());

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            asyncOrderManager.fail(event.getUuid());
        }
    }
}
