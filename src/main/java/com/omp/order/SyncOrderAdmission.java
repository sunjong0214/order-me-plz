package com.omp.order;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * 동기 주문의 동시 처리 수 상한(비교군 "동기 + 빠른 거절", omp.order.sync.max-in-flight). 상한을 넘으면 트랜잭션(커넥션 획득)에
 * 들어가지 않고 즉시 거절한다. 상한이 0이면 아무것도 세지 않는다(원래 동기).
 *
 * 왜 커넥션 대기 시간(Hikari connection-timeout)으로 거절하지 않는가: 동기 요청은 Tomcat 요청 스레드(최대 200)를 먼저 잡고 들어오므로
 * 커넥션을 기다리는 요청은 많아야 200 − P개다. P=20 커넥션이 초당 약 2,360건을 처리하면 그 줄은 약 76ms에 빠져, Hikari 최솟값
 * 250ms로도 시간 초과가 나지 않는다. 넘친 요청은 Tomcat 대기열에서 기다리며 지연만 늘어난다. 그래서 요청 입구에서 센다.
 */
@Component
public class SyncOrderAdmission {
    private final Semaphore permits;
    private final Counter rejected;

    public SyncOrderAdmission(SyncOrderProperties props, MeterRegistry registry) {
        this.permits = props.isLimited() ? new Semaphore(props.maxInFlight()) : null;
        this.rejected = Counter.builder("omp.order.sync.rejected")
                .description("동시 처리 상한을 넘어 거절한 동기 주문 수")
                .register(registry);
        if (permits != null) {
            Gauge.builder("omp.order.sync.permits.available", permits, Semaphore::availablePermits)
                    .description("남은 동기 처리 자리 수")
                    .register(registry);
        }
    }

    /** 처리 자리를 잡는다. 없으면 기다리지 않고 SyncCapacityExceededException(→ 429 + Retry-After). 상한이 없으면 빈 자리를 돌려준다. */
    public Permit acquire() {
        if (permits == null) {
            return new Permit(false);
        }
        if (!permits.tryAcquire()) {
            rejected.increment();
            throw new SyncCapacityExceededException();
        }
        return new Permit(true);
    }

    /** 잡은 자리 하나. 요청이 끝나면(성공·실패 모두) close()로 반납한다. 여러 번 불려도 한 번만 반납한다. */
    public final class Permit implements AutoCloseable {
        private final AtomicBoolean held;

        private Permit(boolean held) {
            this.held = new AtomicBoolean(held);
        }

        @Override
        public void close() {
            if (held.compareAndSet(true, false)) {
                permits.release();
            }
        }
    }
}
