package com.omp.promotion;

import com.omp.order.async.AsyncCapacityExceededException;
import com.omp.order.async.AsyncOrderAdmission;
import com.omp.order.async.AsyncOrderHandler;
import com.omp.order.async.AsyncOrderManager;
import com.omp.order.async.OrderIdentifier;
import com.omp.order.async.OrderProcessingContext;
import com.omp.order.dto.CreateOrderRequest;
import com.omp.promotion.PromotionProperties.StockMode;
import com.omp.promotion.PromotionTicketAllocator.TicketReservation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 할인 주문의 입구(트랜잭션 없음). 기간 확인 → (MEMORY) 번호표 예약 → 저장 트랜잭션(동기) 또는 워커 제출(비동기).
 * 번호표는 트랜잭션보다 먼저 잡아야 하므로 트랜잭션 경계는 PromotionOrderWriter에 둔다.
 */
@Service
@RequiredArgsConstructor
public class PromotionOrderService {
    private final PromotionCatalog catalog;
    private final PromotionTicketAllocator allocator;
    private final PromotionOrderWriter writer;
    private final PromotionProperties props;
    private final AsyncOrderHandler asyncOrderHandler;
    private final AsyncOrderManager asyncOrderManager;

    /** 동기: 응답 = 저장 완료. 매진·이미 참여·기간 밖은 PromotionRejectedException(→ 409). */
    public Long placeSync(CreateOrderRequest request) {
        PromotionCatalog.Info promotion = catalog.requireOpen(request.getPromotionId());
        int bucket = Math.floorMod(request.getOrdererId(), props.buckets());
        if (props.stockMode() != StockMode.MEMORY) {
            return save(command(request, null, bucket));
        }
        try (TicketReservation reservation = allocator.reserve(promotion, request.getOrdererId())) {
            return save(command(request, reservation, bucket));
        }
    }

    /**
     * 비동기: 대기 자리는 호출자가 이미 잡았다. MEMORY는 접수에서 매진·이미 참여를 바로 알려 주고,
     * DB 방식은 접수에서 DB를 쓰지 않으므로 워커에서 판정한 결과를 FAILED + 코드로 알린다.
     */
    public String placeAsync(CreateOrderRequest request, AsyncOrderAdmission.Slot slot) {
        PromotionCatalog.Info promotion = catalog.requireOpen(request.getPromotionId());
        String uuid = UUID.randomUUID().toString();
        if (props.stockMode() != StockMode.MEMORY) {
            submit(uuid, request, slot, null);
            return uuid;
        }
        try (TicketReservation reservation = allocator.reserve(promotion, request.getOrdererId())) {
            submit(uuid, request, slot, reservation);
        }
        return uuid;
    }

    private void submit(String uuid, CreateOrderRequest request, AsyncOrderAdmission.Slot slot, TicketReservation reservation) {
        PromotionOrderCommand cmd = command(request, reservation, PromotionOrderCommand.ANY_WORKER);
        asyncOrderManager.put(uuid, new OrderProcessingContext(
                new OrderIdentifier(request.getOrdererId(), request.getShopId(), request.getCartId())));
        if (reservation != null) {
            reservation.transferToWorker();   // 이제 반납 책임은 워커에 있다
        }
        try {
            asyncOrderHandler.submitWork(uuid, slot, () -> {
                try {
                    return save(cmd);
                } finally {
                    if (reservation != null) {
                        reservation.closeInWorker("worker_before_tx");   // 트랜잭션에 묶였으면 아무것도 하지 않는다
                    }
                }
            });
        } catch (RuntimeException e) {   // 워커에 닿지 못했다: 여기서 반납
            if (reservation != null) {
                reservation.closeInWorker(e instanceof AsyncCapacityExceededException ? "queue_full" : "submit_failed");
            }
            throw e;
        }
    }

    /** DB_BUCKET만 READ COMMITTED 트랜잭션으로 저장한다(PromotionOrderWriter 설명 참고). */
    private Long save(PromotionOrderCommand cmd) {
        return props.stockMode() == StockMode.DB_BUCKET ? writer.writeReadCommitted(cmd) : writer.write(cmd);
    }

    private static PromotionOrderCommand command(CreateOrderRequest request, TicketReservation reservation, int bucket) {
        return new PromotionOrderCommand(request.getPromotionId(), request.getOrdererId(), request.getCartId(),
                request.getShopId(), request.getOrderMenus(), reservation, bucket);
    }
}
