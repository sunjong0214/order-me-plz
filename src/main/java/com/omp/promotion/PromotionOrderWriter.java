package com.omp.promotion;

import com.omp.order.Order;
import com.omp.order.OrderRepository;
import com.omp.order.OrderValidator;
import com.omp.order.dto.CreateOrderRequest;
import com.omp.orderMenu.OrderMenuService;
import com.omp.promotion.PromotionProperties.StockMode;
import com.omp.promotion.PromotionRejectedException.Reason;
import com.omp.promotion.PromotionTicketAllocator.TicketReservation;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 할인 주문 저장 트랜잭션 하나. 동기는 요청 스레드에서, 비동기는 워커에서 부른다.
 *
 * 순서: (DB 방식) 잠그지 않는 조회로 매진이면 바로 거절 → 검증 → 주문·메뉴 INSERT → 참여 INSERT → (DB 방식) 재고 UPDATE.
 * 재고 UPDATE를 마지막에 두는 이유: 조건부 UPDATE가 잡은 행 잠금은 커밋까지 유지되므로, 잠금을 쥐는 시간을 커밋 직전으로 줄인다.
 * MEMORY 방식은 이미 받은 번호표를 참여 행에 적고, 트랜잭션 결과로 확정·반납을 정한다(PromotionTicketAllocator).
 *
 * REQUIRES_NEW: 워커·요청 스레드 어디서 불러도 항상 독립 트랜잭션이다(AsyncOrderProcessor와 같은 방어선).
 *
 * 격리 수준: DB_SINGLE·MEMORY는 DB 기본값(write). DB_BUCKET만 READ COMMITTED(writeReadCommitted)다.
 * REPEATABLE READ에서는 조건부 UPDATE의 조건이 맞지 않아도(빈 행) 그 행의 잠금이 커밋까지 남는다. 여러 행을 돌며 차감하면
 * 트랜잭션 1이 A를 쥔 채 B로, 트랜잭션 2가 B를 쥔 채 A로 가 교착이 된다(2026-09-29 동시 폭주 테스트에서 실제로 발생, 교착 기록 확인).
 * READ COMMITTED는 조건이 맞지 않는 행의 잠금을 바로 풀고, 다른 트랜잭션이 쥔 행도 최신 커밋 값이 조건에 맞지 않으면
 * 기다리지 않고 넘어간다(semi-consistent read). 차감에 성공한 트랜잭션은 그 행 하나만 쥐고 곧 커밋하므로 교착 고리가 생기지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PromotionOrderWriter {
    private static final AtomicInteger NEXT_WORKER = new AtomicInteger();
    private static final ThreadLocal<Integer> WORKER_INDEX = ThreadLocal.withInitial(NEXT_WORKER::getAndIncrement);

    private final OrderValidator orderValidator;
    private final OrderRepository orderRepository;
    private final OrderMenuService orderMenuService;
    private final JdbcTemplate jdbc;
    private final PromotionProperties props;
    private final PromotionMetrics metrics;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long write(PromotionOrderCommand cmd) {
        return doWrite(cmd);
    }

    /** DB_BUCKET 전용. 격리 수준을 지정한 이유는 클래스 설명 참고. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Long writeReadCommitted(PromotionOrderCommand cmd) {
        return doWrite(cmd);
    }

    private Long doWrite(PromotionOrderCommand cmd) {
        TicketReservation reservation = cmd.reservation();
        if (reservation != null) {
            reservation.bindToCurrentTransaction();
        }
        metrics.countConfirmedOnCommit();

        StockMode mode = props.stockMode();
        int[] buckets = null;
        if (mode == StockMode.DB_SINGLE && remainingSingle(cmd.promotionId()) <= 0) {
            throw metrics.rejected(Reason.SOLD_OUT);
        }
        if (mode == StockMode.DB_BUCKET) {
            buckets = bucketSnapshot(cmd.promotionId());
            if (sum(buckets) <= 0) {
                throw metrics.rejected(Reason.SOLD_OUT);
            }
        }
        if (mode == StockMode.MEMORY && reservation == null) {
            throw new IllegalStateException("MEMORY 방식은 번호표 예약이 필요하다");
        }

        orderValidator.validate(cmd.ordererId(), cmd.shopId(), cmd.cartId());
        Long orderId = orderRepository.save(new Order(cmd.ordererId(), cmd.cartId(), cmd.shopId())).getId();
        if (cmd.orderMenus() != null && !cmd.orderMenus().isEmpty()) {
            orderMenuService.createOrderMenus(CreateOrderRequest.from(cmd.orderMenus()));
        }
        insertParticipation(cmd, orderId, reservation);

        if (mode == StockMode.DB_SINGLE) {
            decrementSingle(cmd.promotionId());
        } else if (mode == StockMode.DB_BUCKET) {
            decrementBucket(cmd.promotionId(), buckets, startBucket(cmd, buckets.length));
        }
        return orderId;
    }

    private void insertParticipation(PromotionOrderCommand cmd, Long orderId, TicketReservation reservation) {
        try {
            jdbc.update("INSERT INTO promotion_participations (promotion_id, user_id, order_id, ticket_no, created_at) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    cmd.promotionId(), cmd.ordererId(), orderId,
                    reservation == null ? null : reservation.ticketNo(),
                    Timestamp.valueOf(LocalDateTime.now()));
        } catch (DuplicateKeyException e) {
            if (e.getMessage() != null && e.getMessage().contains(PromotionParticipation.TICKET_UNIQUE)) {
                throw ticketConstraintViolated(reservation, e);
            }
            if (reservation != null) {
                reservation.markAlreadyJoined();   // 번호는 돌려주되 사용자 표시는 남긴다(이 사용자의 참여 행이 이미 있다)
            }
            throw metrics.rejected(Reason.ALREADY_JOINED);
        } catch (DataIntegrityViolationException e) {   // 외래 키: 번호표 테이블에 없는(범위 밖) 번호
            throw ticketConstraintViolated(reservation, e);
        }
    }

    private IllegalStateException ticketConstraintViolated(TicketReservation reservation, RuntimeException cause) {
        if (reservation != null) {
            reservation.markTicketInUse();   // 발급기와 DB가 어긋났다. 이 번호는 다시 내보내지 않는다
        }
        metrics.constraintViolation();
        log.error("promotion ticket constraint violated: {}", cause.getMessage());
        return new IllegalStateException("번호표 제약 위반", cause);
    }

    private int remainingSingle(long promotionId) {
        Integer n = jdbc.queryForObject("SELECT remaining_stock FROM promotions WHERE promotion_id = ?",
                Integer.class, promotionId);
        return n == null ? 0 : n;
    }

    private void decrementSingle(long promotionId) {
        int changed = jdbc.update("UPDATE promotions SET remaining_stock = remaining_stock - 1 "
                + "WHERE promotion_id = ? AND remaining_stock > 0", promotionId);
        if (changed == 0) {
            throw metrics.rejected(Reason.SOLD_OUT);
        }
    }

    /** 행마다 남은 수(잠그지 않는 조회). 인덱스 = bucket_no. */
    private int[] bucketSnapshot(long promotionId) {
        List<int[]> rows = jdbc.query("SELECT bucket_no, remaining FROM promotion_stock_buckets WHERE promotion_id = ?",
                (rs, i) -> new int[]{rs.getInt(1), rs.getInt(2)}, promotionId);
        int[] remaining = new int[rows.size()];
        for (int[] row : rows) {
            remaining[row[0]] = row[1];
        }
        return remaining;
    }

    private int startBucket(PromotionOrderCommand cmd, int bucketCount) {
        int start = cmd.preferredBucket() >= 0 ? cmd.preferredBucket() : WORKER_INDEX.get();
        return Math.floorMod(start, bucketCount);
    }

    /** 시작 행부터 남아 있어 보이는 행에만 조건부 UPDATE. 전부 실패하면 매진. */
    private void decrementBucket(long promotionId, int[] snapshot, int start) {
        for (int i = 0; i < snapshot.length; i++) {
            int bucket = (start + i) % snapshot.length;
            if (snapshot[bucket] <= 0) {
                continue;
            }
            int changed = jdbc.update("UPDATE promotion_stock_buckets SET remaining = remaining - 1 "
                    + "WHERE promotion_id = ? AND bucket_no = ? AND remaining > 0", promotionId, bucket);
            if (changed == 1) {
                return;
            }
        }
        throw metrics.rejected(Reason.SOLD_OUT);
    }

    private static int sum(int[] values) {
        int s = 0;
        for (int v : values) {
            s += v;
        }
        return s;
    }
}
