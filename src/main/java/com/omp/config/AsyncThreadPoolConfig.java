package com.omp.config;

import com.omp.config.ExecutorProperties.Pool;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@RequiredArgsConstructor
public class AsyncThreadPoolConfig {
    private final ExecutorProperties props;

    @Bean(name = "insertTaskExecutor")
    public Executor insertTaskExecutor() {
        return build(props.getInsert(), "AsyncTask-");
    }

    // 리뷰 통계 갱신 전용 풀. @Async 기본 executor(SimpleAsyncTaskExecutor)는
    // 요청마다 새 스레드를 만들어 부하 시 스레드가 무한정 늘어나므로 반드시 명시적으로 지정한다.
    @Bean(name = "reviewStatsExecutor")
    public Executor reviewStatsExecutor() {
        return build(props.getReviewStats(), "ReviewStats-");
    }

    // 거절 정책은 기본 AbortPolicy. CallerRunsPolicy는 포화 시 작업을 요청 스레드(AFTER_COMMIT 콜백 안)에서
    // 실행해 이미 커밋된 트랜잭션에 참여시키고, 지연 격리라는 비동기 구조의 전제도 깨뜨린다.
    // 거절은 호출부가 잡아 카운터·로그·503으로 드러낸다.
    private static ThreadPoolTaskExecutor build(Pool pool, String prefix) {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(pool.getCore());
        taskExecutor.setMaxPoolSize(pool.getMax());
        taskExecutor.setQueueCapacity(pool.getQueue());
        taskExecutor.setThreadNamePrefix(prefix);
        return taskExecutor;
    }
}
