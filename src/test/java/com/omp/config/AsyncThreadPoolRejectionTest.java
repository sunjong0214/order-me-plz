package com.omp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omp.config.ExecutorProperties.Pool;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 풀이 포화되면 작업을 호출 스레드에서 몰래 실행(CallerRunsPolicy)하지 말고 명시적으로 거절해야 한다.
 * CallerRuns는 AFTER_COMMIT 리스너 안에서 호출 스레드가 이미 커밋된 트랜잭션에 참여해 작업을 유실시킬 수 있다.
 */
class AsyncThreadPoolRejectionTest {

    private final ExecutorProperties props = new ExecutorProperties(
            new Pool(1, 1, 1), new Pool(1, 1, 1), new Pool(1, 1, 1));
    private final AsyncThreadPoolConfig config = new AsyncThreadPoolConfig(props);

    @Test
    void insertTaskExecutor_포화_시_거절하고_호출_스레드에서_실행하지_않는다() throws Exception {
        assertRejectsWhenSaturated((ThreadPoolTaskExecutor) config.insertTaskExecutor());
    }

    @Test
    void reviewStatsExecutor_포화_시_거절하고_호출_스레드에서_실행하지_않는다() throws Exception {
        assertRejectsWhenSaturated((ThreadPoolTaskExecutor) config.reviewStatsExecutor());
    }

    private static void assertRejectsWhenSaturated(ThreadPoolTaskExecutor executor) throws Exception {
        executor.initialize();
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.execute(await(release));   // 유일한 스레드 점유
            executor.execute(await(release));   // 큐(용량 1) 채움

            AtomicReference<String> ranOn = new AtomicReference<>();
            assertThatThrownBy(() -> executor.execute(() -> ranOn.set(Thread.currentThread().getName())))
                    .isInstanceOf(RejectedExecutionException.class);
            assertThat(ranOn.get()).as("거절된 작업이 어디서도 실행되면 안 된다").isNull();
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    private static Runnable await(CountDownLatch latch) {
        return () -> {
            try {
                latch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
