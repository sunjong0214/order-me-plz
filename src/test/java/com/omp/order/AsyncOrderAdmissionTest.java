package com.omp.order;

import static com.omp.support.TestFixtures.CART_OK;
import static com.omp.support.TestFixtures.SHOP_OPEN;
import static com.omp.support.TestFixtures.USER_BANNED;
import static com.omp.support.TestFixtures.USER_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.omp.order.async.AsyncOrderProcessor;
import com.omp.support.TestFixtures;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 대기 자리 예약(AsyncOrderAdmission): 워커 1, 큐 1 → 대기 자리 1.
 * 자리가 없으면 접수 트랜잭션에 들어가지 않고 즉시 429이며(검증 쿼리 0회), 자리는 워커가 작업을 시작하거나 검증이 실패하면 돌아온다.
 * 저장 단계는 mock으로 붙잡아 자리를 채운다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "omp.executor.insert.core=1",
        "omp.executor.insert.max=1",
        "omp.executor.insert.queue=1",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class AsyncOrderAdmissionTest {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeterRegistry meterRegistry;
    @Autowired EntityManagerFactory emf;
    @MockitoBean AsyncOrderProcessor asyncOrderProcessor;

    @BeforeEach
    void seed() {
        TestFixtures.resetAndSeed(jdbc);
    }

    @Test
    void 대기_자리가_없으면_검증_쿼리_없이_즉시_429다() {
        CountDownLatch hold = new CountDownLatch(1);
        when(asyncOrderProcessor.processOrderTask(any())).thenAnswer(inv -> {
            hold.await(10, TimeUnit.SECONDS);
            return 1L;
        });
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        double rejectedBefore = rejectedCount();

        try {
            assertThat(post(USER_OK).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);   // 워커가 꺼내 실행(붙잡힘)
            assertThat(TestFixtures.awaitUntil(() -> slotsAvailable() == 1, Duration.ofSeconds(5)))
                    .as("워커가 작업을 시작하면 자리를 반납한다").isTrue();
            assertThat(post(USER_OK).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);   // 큐 한 칸 차지
            assertThat(slotsAvailable()).isZero();

            long queriesBefore = stats.getQueryExecutionCount();
            ResponseEntity<String> overflow = post(USER_OK);

            assertThat(overflow.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(overflow.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
            assertThat(stats.getQueryExecutionCount()).as("거절된 요청은 검증 쿼리를 실행하지 않는다").isEqualTo(queriesBefore);
            assertThat(rejectedCount() - rejectedBefore).isEqualTo(1.0);
        } finally {
            hold.countDown();
        }

        verify(asyncOrderProcessor, timeout(5000).times(2)).processOrderTask(any());
        assertThat(TestFixtures.awaitUntil(() -> slotsAvailable() == 1, Duration.ofSeconds(5))).isTrue();
    }

    @Test
    void 검증에_실패하면_자리를_바로_반납한다() {
        assertThat(slotsAvailable()).isEqualTo(1);

        assertThat(post(USER_BANNED).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(slotsAvailable()).isEqualTo(1);
        verify(asyncOrderProcessor, never()).processOrderTask(any());
    }

    private double slotsAvailable() {
        return meterRegistry.get("omp.order.async.slots.available").gauge().value();
    }

    private double rejectedCount() {
        var counter = meterRegistry.find("omp.order.async.rejected").counter();
        return counter == null ? 0.0 : counter.count();
    }

    private ResponseEntity<String> post(long ordererId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"ordererId\": " + ordererId + ", \"cartId\": " + CART_OK
                + ", \"shopId\": " + SHOP_OPEN + ", \"orderMenus\": []}";
        return rest.postForEntity("/api/v1/order/async", new HttpEntity<>(body, headers), String.class);
    }
}
