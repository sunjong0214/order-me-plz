package com.omp.order;

import static com.omp.support.TestFixtures.CART_OK;
import static com.omp.support.TestFixtures.SHOP_OPEN;
import static com.omp.support.TestFixtures.USER_OK;
import static org.assertj.core.api.Assertions.assertThat;

import com.omp.support.TestFixtures;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * insertTaskExecutor를 스레드 1, 큐 1로 줄여 포화를 강제한다.
 * 기대: 큐에 들어간 주문은 202, 넘친 주문은 503 + Retry-After, 그리고 202를 받은 주문만 정확히 저장된다.
 * (기존 CallerRunsPolicy는 넘친 주문을 요청 스레드에서 실행해 202를 돌려주면서도 저장을 유실할 수 있었다.)
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "omp.executor.insert.core=1",
        "omp.executor.insert.max=1",
        "omp.executor.insert.queue=1"
})
class AsyncOrderRejectionTest {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeterRegistry meterRegistry;
    @Autowired @Qualifier("insertTaskExecutor") Executor insertTaskExecutor;

    @BeforeEach
    void seed() {
        TestFixtures.resetAndSeed(jdbc);
    }

    @Test
    void 풀_포화_시_503을_반환하고_접수된_주문만_저장된다() throws Exception {
        double rejectedBefore = rejectedCount();
        CountDownLatch hold = new CountDownLatch(1);
        insertTaskExecutor.execute(() -> awaitQuietly(hold));   // 유일한 워커 스레드 점유

        try {
            ResponseEntity<String> queued = postAsyncOrder();      // 큐 1칸 차지
            ResponseEntity<String> overflow = postAsyncOrder();    // 넘침

            assertThat(queued.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(overflow.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(overflow.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
            assertThat(rejectedCount() - rejectedBefore).isEqualTo(1.0);
        } finally {
            hold.countDown();
        }

        boolean saved = TestFixtures.awaitUntil(() -> TestFixtures.count(jdbc, "orders") == 1, Duration.ofSeconds(5));
        assertThat(saved).as("202를 받은 주문 1건만 저장되어야 한다").isTrue();
        Thread.sleep(300);   // 거절된 주문이 뒤늦게 저장되는지 확인할 여유
        assertThat(TestFixtures.count(jdbc, "orders")).isEqualTo(1);
    }

    private double rejectedCount() {
        var counter = meterRegistry.find("omp.order.async.rejected").counter();
        return counter == null ? 0.0 : counter.count();
    }

    private ResponseEntity<String> postAsyncOrder() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"ordererId\": " + USER_OK + ", \"cartId\": " + CART_OK
                + ", \"shopId\": " + SHOP_OPEN + ", \"orderMenus\": []}";
        return rest.postForEntity("/api/v1/order/async", new HttpEntity<>(body, headers), String.class);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
