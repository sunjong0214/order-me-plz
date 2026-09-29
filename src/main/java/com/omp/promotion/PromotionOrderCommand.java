package com.omp.promotion;

import com.omp.orderMenu.OrderMenuRequest;
import com.omp.promotion.PromotionTicketAllocator.TicketReservation;
import java.util.List;

/**
 * 할인 주문 저장 명령.
 * reservation: MEMORY 방식에서만 있다. preferredBucket: DB_BUCKET의 시작 행(동기는 사용자 id로 정하고,
 * 비동기는 ANY_WORKER로 두면 워커마다 자기 행을 쓴다).
 */
public record PromotionOrderCommand(long promotionId,
                                    long ordererId,
                                    long cartId,
                                    long shopId,
                                    List<OrderMenuRequest> orderMenus,
                                    TicketReservation reservation,
                                    int preferredBucket) {
    public static final int ANY_WORKER = -1;
}
