package com.omp.order;

import com.omp.delivery.dto.CreateAsyncOrderEvent;
import com.omp.order.async.AsyncOrderHandler;
import com.omp.order.async.AsyncOrderManager;
import com.omp.order.async.OrderIdentifier;
import com.omp.order.async.OrderJobState;
import com.omp.order.async.OrderProcessingContext;
import com.omp.order.dto.CreateOrderRequest;
import com.omp.order.dto.OrderValidateDto;
import com.omp.orderMenu.OrderMenuService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동기·비동기 주문은 검증 규칙·격리 수준·저장 데이터가 같고 "INSERT를 요청 스레드에서 하느냐, 워커에서 하느냐"만 다르다.
 * 검증은 두 경로 모두 validateOrder 단일 쿼리 1회.
 */
@RequiredArgsConstructor
@Service
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderMenuService orderMenuService;
    private final AsyncOrderManager asyncOrderManager;
    private final AsyncOrderHandler asyncOrderHandler;

    public Order findOrderBy(final Long id) {
        return orderRepository.findById(id).orElseThrow();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long saveOrderBy(final CreateOrderRequest request) {
        validate(request.getOrdererId(), request.getShopId(), request.getCartId());

        Order newOrder = orderRepository.save(CreateOrderRequest.from(request));
        orderMenuService.createOrderMenus(CreateOrderRequest.from(request.getOrderMenus()));

        return newOrder.getId();
    }

    /**
     * 접수: 검증 후 INSERT 작업을 워커 풀에 제출하고 작업 식별자를 반환한다. 이 트랜잭션은 검증 SELECT만 수행한다.
     * 제출은 이벤트가 아닌 직접 호출이다. 풀 포화 거절이 예외로 전파되어 503이 되어야 하기 때문(AsyncOrderHandler 참고).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String asyncOrder(CreateOrderRequest request) {
        Long ordererId = request.getOrdererId();
        Long shopId = request.getShopId();
        Long cartId = request.getCartId();
        validate(ordererId, shopId, cartId);

        String uuid = UUID.randomUUID().toString();
        asyncOrderManager.put(uuid, new OrderProcessingContext(new OrderIdentifier(ordererId, shopId, cartId)));
        asyncOrderHandler.submit(new CreateAsyncOrderEvent(ordererId, cartId, shopId, uuid, request.getOrderMenus()));
        return uuid;
    }

    private void validate(Long ordererId, Long shopId, Long cartId) {
        OrderValidateDto validDto = orderRepository.validateOrder(ordererId, shopId, cartId);
        // 세 행 중 하나라도 없으면 조인 결과가 없어 null이 온다.
        if (validDto == null || !validDto.isValid()) {
            throw new InvalidOrderException(ordererId, shopId, cartId);
        }
    }

    public OrderJobState getOrderState(String uuid) {
        return asyncOrderManager.getJobState(uuid);
    }
}
