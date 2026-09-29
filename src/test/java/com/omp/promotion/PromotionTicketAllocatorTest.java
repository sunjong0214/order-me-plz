package com.omp.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.omp.promotion.PromotionProperties.StockMode;
import com.omp.promotion.PromotionRejectedException.Reason;
import com.omp.promotion.PromotionTicketAllocator.TicketReservation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

/**
 * 번호표 발급기 단독 검증(DB 없음). 동시 예약에서 재고만큼만 나가고 번호가 겹치지 않는지,
 * 트랜잭션 결과(커밋됨·롤백됨·모름)에 따라 확정·반납·확인 대기가 되는지 본다.
 */
class PromotionTicketAllocatorTest {
    private static final PromotionCatalog.Info PROMOTION = new PromotionCatalog.Info(1L, 100,
            LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));

    private JdbcTemplate jdbc;
    private PromotionTicketAllocator allocator;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);   // 참여 행 조회는 빈 결과(새 이벤트)
        allocator = new PromotionTicketAllocator(jdbc, new PromotionMetrics(new SimpleMeterRegistry()),
                new PromotionProperties(StockMode.MEMORY, 20, Duration.ofHours(1), false), new SimpleMeterRegistry());
    }

    @AfterEach
    void tearDown() {
        allocator.destroy();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 동시_예약은_재고만큼만_성공하고_번호가_겹치지_않는다() throws Exception {
        int users = 1_000;
        ExecutorService pool = Executors.newFixedThreadPool(64);
        CountDownLatch start = new CountDownLatch(1);
        Set<Integer> tickets = ConcurrentHashMap.newKeySet();
        AtomicInteger soldOut = new AtomicInteger();
        for (int u = 1; u <= users; u++) {
            long userId = u;
            pool.submit(() -> {
                start.await();
                try {
                    tickets.add(allocator.reserve(PROMOTION, userId).ticketNo());   // 확정 전 상태로 둔다(반납하지 않음)
                } catch (PromotionRejectedException e) {
                    if (e.reason() == Reason.SOLD_OUT) {
                        soldOut.incrementAndGet();
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(tickets).hasSize(100);
        assertThat(tickets).allMatch(t -> t >= 1 && t <= 100);
        assertThat(soldOut.get()).isEqualTo(900);
        assertThat(allocator.remaining(PROMOTION.id())).isZero();
    }

    @Test
    void 같은_사용자는_한_번만_예약된다() {
        allocator.reserve(PROMOTION, 7L);

        assertThatThrownBy(() -> allocator.reserve(PROMOTION, 7L))
                .isInstanceOfSatisfying(PromotionRejectedException.class, e -> assertThat(e.reason()).isEqualTo(Reason.ALREADY_JOINED));
        assertThat(allocator.remaining(PROMOTION.id())).isEqualTo(99);
    }

    @Test
    void 트랜잭션에_넘기기_전에_닫으면_번호와_사용자_표시를_돌려준다() {
        TicketReservation r = allocator.reserve(PROMOTION, 7L);
        r.close();
        r.close();   // 두 번 닫아도 한 번만 반납

        assertThat(allocator.remaining(PROMOTION.id())).isEqualTo(100);
        TicketReservation again = allocator.reserve(PROMOTION, 7L);   // 같은 사용자가 다시 시도할 수 있다
        assertThat(again.ticketNo()).isEqualTo(r.ticketNo());          // 돌려받은 번호가 먼저 나간다
    }

    @Test
    void 커밋되면_확정되고_닫아도_돌려주지_않는다() {
        TicketReservation r = inTransaction(allocator.reserve(PROMOTION, 7L), TransactionSynchronization.STATUS_COMMITTED);
        r.close();

        assertThat(r.state()).isEqualTo(PromotionTicketAllocator.State.CONFIRMED);
        assertThat(allocator.remaining(PROMOTION.id())).isEqualTo(99);
    }

    @Test
    void 롤백되면_돌려준다() {
        TicketReservation r = inTransaction(allocator.reserve(PROMOTION, 7L), TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(r.state()).isEqualTo(PromotionTicketAllocator.State.RELEASED);
        assertThat(allocator.remaining(PROMOTION.id())).isEqualTo(100);
    }

    @Test
    void 결과를_모르면_확인_대기이고_DB에_있으면_확정한다() {
        TicketReservation r = inTransaction(allocator.reserve(PROMOTION, 7L), TransactionSynchronization.STATUS_UNKNOWN);
        assertThat(allocator.pendingCount()).isEqualTo(1);
        assertThat(allocator.remaining(PROMOTION.id())).isEqualTo(99);   // 확인 전에는 돌려주지 않는다

        participationRowCount(1);
        allocator.reconcileNow();

        assertThat(r.state()).isEqualTo(PromotionTicketAllocator.State.CONFIRMED);
        assertThat(allocator.pendingCount()).isZero();
        assertThat(allocator.remaining(PROMOTION.id())).isEqualTo(99);
    }

    @Test
    void 결과를_모르면_확인_대기이고_DB에_없으면_돌려준다() {
        TicketReservation r = inTransaction(allocator.reserve(PROMOTION, 7L), TransactionSynchronization.STATUS_UNKNOWN);

        participationRowCount(0);
        allocator.reconcileNow();

        assertThat(r.state()).isEqualTo(PromotionTicketAllocator.State.RELEASED);
        assertThat(allocator.remaining(PROMOTION.id())).isEqualTo(100);
    }

    @Test
    void 워커에_넘긴_뒤에는_요청_스레드가_닫아도_돌려주지_않고_워커가_돌려준다() {
        TicketReservation r = allocator.reserve(PROMOTION, 7L);
        r.transferToWorker();
        r.close();
        assertThat(allocator.remaining(PROMOTION.id())).isEqualTo(99);

        r.closeInWorker("worker_before_tx");
        assertThat(allocator.remaining(PROMOTION.id())).isEqualTo(100);
    }

    /** 수동 트랜잭션 동기화로 bind → afterCompletion(status)까지 흉내 낸다. */
    private static TicketReservation inTransaction(TicketReservation r, int status) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            r.bindToCurrentTransaction();
            List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
            TransactionSynchronizationUtils.invokeAfterCompletion(syncs, status);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        return r;
    }

    private void participationRowCount(int n) {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(n);
    }
}
