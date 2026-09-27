package com.omp.order.async;

import com.omp.config.ExecutorProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * 비동기 주문의 대기 자리(insertTaskExecutor 큐 칸)를 접수 트랜잭션보다 먼저 예약한다.
 *
 * 왜 필요한가: 예전에는 접수 트랜잭션(커넥션 획득 → 검증 SELECT)을 마친 뒤 submit에서야 큐가 찼는지 알았다.
 * 과부하에서는 어차피 거절될 요청이 커넥션과 쿼리를 쓰고 건마다 WARN 로그를 남겨, 받은 주문까지 느려졌다
 * (2026-09-26 파일럿 P2: 503 응답도 p95 0.3~0.8초, 회차당 로그 32만~38만 줄). 자리를 먼저 세면 거절은 DB 없이 즉시 끝난다.
 *
 * 자리 수 = 큐 용량. 자리는 워커가 작업을 꺼내 시작할 때 반납한다(그때 큐 한 칸이 실제로 빈다).
 * 저장이 끝날 때 반납하면, 워커가 끝난 뒤 다음 작업을 꺼내기 전의 틈에 들어온 요청이 가득 찬 큐를 만나 거절될 수 있다.
 * 실행 중(최대 max) + 대기(최대 큐 용량) 상한은 예전과 같다. 큐가 넘치지 않으므로 스레드는 core를 넘어 늘지 않는다(core = max로 설정).
 */
@Component
public class AsyncOrderAdmission {
    private final Semaphore slots;
    private final Counter rejected;

    public AsyncOrderAdmission(ExecutorProperties props, MeterRegistry registry) {
        this.slots = new Semaphore(props.getInsert().getQueue());
        this.rejected = Counter.builder("omp.order.async.rejected")
                .description("대기 자리가 없어 접수를 거절한 주문 수")
                .register(registry);
        Gauge.builder("omp.order.async.slots.available", slots, Semaphore::availablePermits)
                .description("남은 대기 자리 수")
                .register(registry);
    }

    /** 자리를 예약한다. 없으면 기다리지 않고 AsyncCapacityExceededException(→ 503 + Retry-After). */
    public Slot acquire() {
        if (!slots.tryAcquire()) {
            throw reject();
        }
        return new Slot();
    }

    /** 거절을 센다. 자리 예약 뒤에도 executor가 거절하는 경우(정상이면 발생하지 않음)의 안전망에서도 쓴다. */
    AsyncCapacityExceededException reject() {
        rejected.increment();
        return new AsyncCapacityExceededException();
    }

    /**
     * 예약한 자리 하나. 워커에 넘기기 전에 요청이 끝나면(검증 실패·예외) 요청 스레드가 close()로 반납하고,
     * 넘긴 뒤에는 워커가 작업을 시작할 때 반납한다. 반납은 몇 번 호출돼도 한 번만 일어난다.
     */
    public final class Slot implements AutoCloseable {
        private final AtomicBoolean released = new AtomicBoolean();
        private volatile boolean handedOff;

        void handOff() {
            handedOff = true;
        }

        void release() {
            if (released.compareAndSet(false, true)) {
                slots.release();
            }
        }

        @Override
        public void close() {
            if (!handedOff) {
                release();
            }
        }
    }
}
