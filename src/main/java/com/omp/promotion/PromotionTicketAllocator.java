package com.omp.promotion;

import com.omp.promotion.PromotionRejectedException.Reason;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.BitSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * MEMORY 방식의 번호표 발급기: 재고 판정을 DB 트랜잭션 밖에서 원자적으로 한다(설계 3.3~3.5).
 *
 * 원칙: 메모리는 빠른 판정, DB가 최종 보증. 번호는 1~총 재고만 나가고, 같은 번호의 중복 사용과 범위 밖 번호는
 * promotion_participations의 유니크·외래 키가 막는다. 번호를 돌려주는 조건은 "그 주문이 DB에 확실히 없다"뿐이다.
 * - 트랜잭션에 넘기기 전에 끝나면 요청 스레드(close) 또는 워커(closeInWorker)가 돌려준다
 * - 트랜잭션에 넘긴 뒤에는 Spring이 알려 주는 결과로 정한다: 커밋됨 → 확정, 롤백됨 → 반납,
 *   모름(커밋 호출 실패 등) → 확인 대기. 대조기가 (이벤트, 사용자) 참여 행이 있는지 보고 확정하거나 반납한다
 *   (트랜잭션 매니저의 rollbackOnCommitFailure는 기본값 false로 둔다. 켜면 커밋 실패 뒤 롤백을 시도해 성공하면 "롤백됨"을 알리는데,
 *   실제로는 커밋됐고 응답만 잃은 경우에도 그렇게 되어 번호를 돌려주면 초과 판매가 된다)
 * - 기동(또는 reload) 뒤 처음 쓸 때 참여 행으로 쓰인 번호와 참여자를 다시 읽는다. 사라진 큐의 주문 몫은 저절로 돌아온다
 * 서버 한 대 전제다. 여러 대면 공용 발급기(Redis 원자 연산 등)가 필요하다.
 */
@Component
@Slf4j
public class PromotionTicketAllocator implements DisposableBean {
    private final JdbcTemplate jdbc;
    private final PromotionMetrics metrics;
    private final Map<Long, Pool> pools = new ConcurrentHashMap<>();
    private final Queue<TicketReservation> pending = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService reconciler;

    public PromotionTicketAllocator(JdbcTemplate jdbc, PromotionMetrics metrics, PromotionProperties props,
                                    MeterRegistry registry) {
        this.jdbc = jdbc;
        this.metrics = metrics;
        Gauge.builder("omp.promotion.remaining", pools, ps -> ps.values().stream().mapToInt(Pool::remaining).sum())
                .description("메모리 발급기에 남은 번호표 수").register(registry);
        Gauge.builder("omp.promotion.unknown.pending", pending, Queue::size)
                .description("커밋 결과를 몰라 DB 확인을 기다리는 예약 수").register(registry);
        this.reconciler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "promotion-reconciler");
            t.setDaemon(true);
            return t;
        });
        long interval = props.reconcileInterval().toMillis();
        reconciler.scheduleWithFixedDelay(this::reconcileQuietly, interval, interval, TimeUnit.MILLISECONDS);
    }

    /** 번호표 하나를 예약한다. 같은 사용자의 예약이 있으면 ALREADY_JOINED, 번호가 없으면 SOLD_OUT. DB를 쓰지 않는다. */
    public TicketReservation reserve(PromotionCatalog.Info promotion, long userId) {
        Pool pool = pools.computeIfAbsent(promotion.id(), id -> load(promotion));
        if (!pool.participants.add(userId)) {
            throw metrics.rejected(Reason.ALREADY_JOINED);
        }
        Integer ticket = pool.take();
        if (ticket == null) {
            pool.participants.remove(userId);
            throw metrics.rejected(Reason.SOLD_OUT);
        }
        metrics.reserved();
        return new TicketReservation(pool, userId, ticket);
    }

    /** 확인 대기 예약을 DB와 대조한다. 대조기가 주기적으로 부르고, 테스트는 직접 부른다. */
    public void reconcileNow() {
        for (int i = pending.size(); i > 0; i--) {
            TicketReservation r = pending.poll();
            if (r == null) {
                return;
            }
            try {
                Integer n = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM promotion_participations WHERE promotion_id = ? AND user_id = ?",
                        Integer.class, r.promotionId(), r.userId());
                if (n != null && n > 0) {
                    r.confirmPending();
                } else {
                    r.releasePending();
                }
            } catch (DataAccessException e) {
                pending.add(r);   // DB가 아직 응답하지 않는다. 다음 주기에 다시
                log.warn("promotion reconcile failed, retry later: {}", e.getMessage());
                return;
            }
        }
    }

    /** 메모리 상태를 버린다. 다음 예약 때 DB에서 다시 읽는다(이벤트 초기화·테스트용). */
    public void reload(long promotionId) {
        pools.remove(promotionId);
        pending.removeIf(r -> r.promotionId() == promotionId);
    }

    public int remaining(long promotionId) {
        Pool pool = pools.get(promotionId);
        return pool == null ? -1 : pool.remaining();
    }

    public int pendingCount() {
        return pending.size();
    }

    @Override
    public void destroy() {
        reconciler.shutdownNow();
    }

    private void reconcileQuietly() {
        try {
            reconcileNow();
        } catch (RuntimeException e) {
            log.warn("promotion reconcile error: {}", e.getMessage());
        }
    }

    private Pool load(PromotionCatalog.Info promotion) {
        Pool pool = new Pool(promotion.id(), promotion.totalStock());
        BitSet used = new BitSet(promotion.totalStock() + 1);
        jdbc.query("SELECT user_id, ticket_no FROM promotion_participations WHERE promotion_id = ?", rs -> {
            pool.participants.add(rs.getLong(1));
            int ticket = rs.getInt(2);
            if (!rs.wasNull()) {
                used.set(ticket);
            }
        }, promotion.id());
        if (!pool.participants.isEmpty()) {   // 재시작: 쓰이지 않은 번호를 모두 반납 목록으로
            for (int t = used.nextClearBit(1); t <= promotion.totalStock(); t = used.nextClearBit(t + 1)) {
                pool.returned.add(t);
            }
            pool.next.set(promotion.totalStock() + 1);
        }
        return pool;
    }

    /** 이벤트 하나의 번호표 상태. 새 번호는 next에서, 돌려받은 번호는 returned에서 나간다. */
    static final class Pool {
        final long promotionId;
        final int total;
        final AtomicInteger next = new AtomicInteger(1);
        final Queue<Integer> returned = new ConcurrentLinkedQueue<>();
        final Set<Long> participants = ConcurrentHashMap.newKeySet();

        Pool(long promotionId, int total) {
            this.promotionId = promotionId;
            this.total = total;
        }

        /** 돌려받은 번호가 먼저, 없으면 새 번호. 둘 다 없으면 null. next는 total + 1을 넘지 않는다. */
        Integer take() {
            Integer t = returned.poll();
            if (t != null) {
                return t;
            }
            int n = next.getAndUpdate(v -> v <= total ? v + 1 : v);
            return n <= total ? n : null;
        }

        int remaining() {
            return Math.max(0, total - next.get() + 1) + returned.size();
        }
    }

    enum State { RESERVED, QUEUED, IN_TX, CONFIRMED, PENDING, RELEASED }

    /**
     * 예약한 번호표 하나. 누가 쥐고 있느냐에 따라 반납 책임이 옮겨 간다: 요청 스레드(RESERVED) → 워커(QUEUED, 비동기만)
     * → 트랜잭션(IN_TX) → 확정(CONFIRMED) / 반납(RELEASED) / 확인 대기(PENDING). 반납은 한 번만 일어난다.
     */
    public final class TicketReservation implements AutoCloseable {
        private final Pool pool;
        private final long userId;
        private final int ticketNo;
        private final AtomicReference<State> state = new AtomicReference<>(State.RESERVED);
        private volatile boolean keepParticipant;   // 이미 참여로 드러남: 사용자 표시는 남긴다
        private volatile boolean ticketInUse;       // 번호 제약 위반: 번호를 돌려주지 않는다

        private TicketReservation(Pool pool, long userId, int ticketNo) {
            this.pool = pool;
            this.userId = userId;
            this.ticketNo = ticketNo;
        }

        public long promotionId() {
            return pool.promotionId;
        }

        public long userId() {
            return userId;
        }

        public int ticketNo() {
            return ticketNo;
        }

        State state() {
            return state.get();
        }

        /** 비동기: 워커에 넘긴다. 이후 요청 스레드의 close()는 아무것도 하지 않는다. */
        public void transferToWorker() {
            state.compareAndSet(State.RESERVED, State.QUEUED);
        }

        /** 현재 트랜잭션에 묶는다. 트랜잭션이 끝나면 결과에 따라 확정·반납·확인 대기가 된다. */
        public void bindToCurrentTransaction() {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                throw new IllegalStateException("활성 트랜잭션 밖에서 번호표를 묶을 수 없다");
            }
            State s = state.get();
            if ((s != State.RESERVED && s != State.QUEUED) || !state.compareAndSet(s, State.IN_TX)) {
                throw new IllegalStateException("번호표 예약을 트랜잭션에 묶을 수 없다: " + s);
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    onCompletion(status);
                }
            });
        }

        void markAlreadyJoined() {
            keepParticipant = true;
        }

        void markTicketInUse() {
            ticketInUse = true;
        }

        /** 요청 스레드: 아직 워커·트랜잭션에 넘기지 않았다면 반납 (try-with-resources). */
        @Override
        public void close() {
            finish(State.RESERVED, "before_tx");
        }

        /** 워커: 트랜잭션에 묶기 전에 끝났다면 반납. */
        public void closeInWorker(String reason) {
            finish(State.QUEUED, reason);
        }

        private void onCompletion(int status) {
            if (status == TransactionSynchronization.STATUS_COMMITTED) {
                state.compareAndSet(State.IN_TX, State.CONFIRMED);
            } else if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                finish(State.IN_TX, "rollback");
            } else if (state.compareAndSet(State.IN_TX, State.PENDING)) {   // STATUS_UNKNOWN
                pending.add(this);
            }
        }

        private void confirmPending() {
            state.compareAndSet(State.PENDING, State.CONFIRMED);
        }

        private void releasePending() {
            finish(State.PENDING, "unknown_absent");
        }

        private void finish(State expected, String reason) {
            if (!state.compareAndSet(expected, State.RELEASED)) {
                return;
            }
            if (!ticketInUse) {
                pool.returned.add(ticketNo);
            }
            if (!keepParticipant) {
                pool.participants.remove(userId);
            }
            metrics.released(reason);
        }
    }
}
