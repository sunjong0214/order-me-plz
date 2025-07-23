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
    private final OrderCreateWebhook webhook;
    private final AsyncOrderManager asyncOrderManager;

    private static final Log log = LogFactory.getLog(AsyncOrderWorker.class);

    @TransactionalEventListener
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void asyncCreateOrder(CreateAsyncOrderEvent event) {
        long start = System.currentTimeMillis();
        // validation
        try {
            Long ordererId = event.getOrdererId();
            validateOrder(ordererId);

            Long shopId = event.getShopId();
            validateShop(shopId);

            Long cartId = event.getCartId();
            validateCart(cartId, ordererId, shopId);

            Order newOrder = orderRepository.save(CreateAsyncOrderEvent.from(event));
            asyncOrderManager.complete(event.getUuid(), newOrder.getId());

            // 웹훅은 tcp 연결과 같이 오래 걸리는 작업 -> 트랜잭션에서 분리할 필요성
            Boolean b = webhook.sendOrderWebhook(
                    new OrderWebhookRequest(event.getUuid(), OrderJobStatus.COMPLETED, newOrder.getId()));
//                    .thenAccept(success -> {
//                        if (!success) {
//                            // 예외처리 방법
//                        }
//                    });

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            asyncOrderManager.fail(event.getUuid());
        }
        System.out.println("경과 시간: " + (System.currentTimeMillis() - start) + "ms");
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
