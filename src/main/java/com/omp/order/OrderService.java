package com.omp.order;

import com.omp.delivery.dto.CreateAsyncOrderEvent;
import com.omp.order.async.AsyncOrderAdmission;
import com.omp.order.async.AsyncOrderHandler;
import com.omp.order.async.AsyncOrderHandler.WorkerTask;
import com.omp.order.async.AsyncOrderManager;
import com.omp.order.async.OrderIdentifier;
import com.omp.order.async.OrderJobState;
import com.omp.order.async.OrderProcessingContext;
import com.omp.order.dto.CreateOrderRequest;
import com.omp.orderMenu.OrderMenuService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동기·비동기 주문은 검증 규칙·격리 수준·저장 데이터가 같고 "INSERT를 요청 스레드에서 하느냐, 워커에서 하느냐"만 다르다.
 * 검증은 모든 경로에서 validateOrder 단일 쿼리 1회. 비동기는 검증 위치에 따라 두 구성이 있다(AsyncOrderProperties).
 */
@RequiredArgsConstructor
@Service
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderMenuService orderMenuService;
    private final OrderValidator orderValidator;
    private final AsyncOrderManager asyncOrderManager;
    private final AsyncOrderHandler asyncOrderHandler;

    public Order findOrderBy(final Long id) {
        return orderRepository.findById(id).orElseThrow();
    }

    /** 격리 수준은 DB 기본값(AsyncOrderProcessor 설명 참고). */
    @Transactional
    public Long saveOrderBy(final CreateOrderRequest request) {
        orderValidator.validate(request.getOrdererId(), request.getShopId(), request.getCartId());

        Order newOrder = orderRepository.save(CreateOrderRequest.from(request));
        orderMenuService.createOrderMenus(CreateOrderRequest.from(request.getOrderMenus()));

        return newOrder.getId();
    }

    /**
     * 비동기 SPLIT 접수: 검증 후 INSERT 작업을 워커 풀에 제출하고 작업 식별자를 반환한다. 이 트랜잭션은 검증 SELECT만 수행한다.
     * 제출은 이벤트가 아닌 직접 호출이다. 거절이 예외로 전파되어 429가 되어야 하기 때문(AsyncOrderHandler 참고).
     * 대기 자리(slot)는 호출자가 트랜잭션 전에 예약해 넘긴다(AsyncOrderAdmission).
     */
    @Transactional
    public String asyncOrder(CreateOrderRequest request, AsyncOrderAdmission.Slot slot) {
        orderValidator.validate(request.getOrdererId(), request.getShopId(), request.getCartId());
        return submit(request, slot, WorkerTask.INSERT_ONLY);
    }

    /**
     * 비동기 SINGLE 접수: DB를 쓰지 않는다(트랜잭션·커넥션 없음). 검증과 INSERT는 워커의 트랜잭션 하나에서 한다.
     * 잘못된 주문도 여기서는 202가 되고, 워커의 검증 실패가 상태 FAILED로 전달된다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String asyncOrderDeferred(CreateOrderRequest request, AsyncOrderAdmission.Slot slot) {
        return submit(request, slot, WorkerTask.VALIDATE_AND_INSERT);
    }

    private String submit(CreateOrderRequest request, AsyncOrderAdmission.Slot slot, WorkerTask task) {
        Long ordererId = request.getOrdererId();
        Long shopId = request.getShopId();
        Long cartId = request.getCartId();
        String uuid = UUID.randomUUID().toString();
        asyncOrderManager.put(uuid, new OrderProcessingContext(new OrderIdentifier(ordererId, shopId, cartId)));
        asyncOrderHandler.submit(new CreateAsyncOrderEvent(ordererId, cartId, shopId, uuid, request.getOrderMenus()), slot, task);
        return uuid;
    }

    public OrderJobState getOrderState(String uuid) {
        return asyncOrderManager.getJobState(uuid);
    }
}
