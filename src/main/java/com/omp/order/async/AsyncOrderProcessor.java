package com.omp.order.async;

import com.omp.delivery.dto.CreateAsyncOrderEvent;
import com.omp.order.OrderRepository;
import com.omp.orderMenu.OrderMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비동기 주문의 실제 저장 단계. 검증은 접수 시점에 동기 경로와 같은 쿼리로 1회 수행했으므로 여기서는 INSERT만 한다.
 * (동기 경로와 DB 작업량을 같게 맞춰 "실행 방식만 다른" 비교가 되도록 한다.)
 *
 * REQUIRES_NEW: 항상 워커 스레드에서 새 트랜잭션으로 실행된다는 것을 코드로 고정한다.
 * 호출 스레드에서 인라인 실행되는 경로(CallerRunsPolicy)가 다시 생겨도 접수 트랜잭션에 참여해 유실되지 않도록 하는 방어선.
 */
@RequiredArgsConstructor
@Component
public class AsyncOrderProcessor {
    private final OrderRepository orderRepository;
    private final OrderMenuService orderMenuService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Long processOrderTask(CreateAsyncOrderEvent event) {
        orderMenuService.createOrderMenus(CreateAsyncOrderEvent.from(event.getOrderMenus()));
        return orderRepository.save(CreateAsyncOrderEvent.from(event)).getId();
    }
}
