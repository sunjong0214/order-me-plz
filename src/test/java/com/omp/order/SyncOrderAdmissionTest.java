package com.omp.order;

import static com.omp.support.TestFixtures.CART_OK;
import static com.omp.support.TestFixtures.SHOP_OPEN;
import static com.omp.support.TestFixtures.USER_BANNED;
import static com.omp.support.TestFixtures.USER_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omp.support.TestFixtures;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

/**
 * 동기 주문의 동시 처리 상한(omp.order.sync.max-in-flight, 비교군 "동기 + 빠른 거절").
 * 상한만큼 처리 중이면 다음 요청은 트랜잭션에 들어가지 않고 즉시 429 + Retry-After이며, 자리는 요청이 끝나면(성공·실패) 돌아온다.
 */
class SyncOrderAdmissionTest {

    @Nested
    class 자리_계산 {
        @Test
        void 상한이_0이면_세지_않는다() {
            SyncOrderAdmission admission = new SyncOrderAdmission(new SyncOrderProperties(0), new SimpleMeterRegistry());

            for (int i = 0; i < 1000; i++) {
                admission.acquire();   // 반납하지 않아도 거절하지 않는다
            }
        }

        @Test
        void 상한을_넘으면_거절하고_반납은_한_번만_일어난다() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            SyncOrderAdmission admission = new SyncOrderAdmission(new SyncOrderProperties(1), registry);

            SyncOrderAdmission.Permit first = admission.acquire();
            assertThatThrownBy(admission::acquire).isInstanceOf(SyncCapacityExceededException.class);
            first.close();
            first.close();   // 두 번째 close는 자리를 늘리지 않는다

            SyncOrderAdmission.Permit second = admission.acquire();
            assertThatThrownBy(admission::acquire).isInstanceOf(SyncCapacityExceededException.class);
            second.close();
            assertThat(registry.get("omp.order.sync.rejected").counter().count()).isEqualTo(2.0);
        }
    }

    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
    @ActiveProfiles("test")
    @TestPropertySource(properties = "omp.order.sync.max-in-flight=1")
    @Nested
    class HTTP {
        @Autowired TestRestTemplate rest;
        @Autowired JdbcTemplate jdbc;
        @Autowired DataSource dataSource;
        @Autowired EntityManagerFactory emf;
        @Autowired MeterRegistry meterRegistry;

        @BeforeEach
        void seed() {
            TestFixtures.resetAndSeed(jdbc);
        }

        @Test
        void 상한만큼_처리_중이면_DB를_쓰지_않고_즉시_429다() throws Exception {
            Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
            double rejectedBefore = rejectedCount();
            CompletableFuture<ResponseEntity<String>> first;

            try (Connection locker = dataSource.getConnection(); Statement lock = locker.createStatement()) {
                lock.execute("LOCK TABLES orders WRITE");   // 첫 주문을 INSERT에서 붙잡아 자리를 계속 쥐게 한다
                try {
                    first = CompletableFuture.supplyAsync(() -> post(USER_OK));
                    assertThat(TestFixtures.awaitUntil(() -> permitsAvailable() == 0, Duration.ofSeconds(5)))
                            .as("첫 주문이 자리를 잡고 처리 중").isTrue();

                    long queriesBefore = stats.getQueryExecutionCount();
                    ResponseEntity<String> overflow = post(USER_OK);

                    assertThat(overflow.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(overflow.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
                    assertThat(stats.getQueryExecutionCount()).as("거절된 요청은 검증 쿼리를 실행하지 않는다").isEqualTo(queriesBefore);
                    assertThat(rejectedCount() - rejectedBefore).isEqualTo(1.0);
                } finally {
                    lock.execute("UNLOCK TABLES");   // 커넥션을 풀에 돌려주기 전에 반드시 해제
                }
            }

            assertThat(first.get(10, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(permitsAvailable()).as("끝난 주문은 자리를 반납한다").isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isEqualTo(1L);
        }

        @Test
        void 검증에_실패해도_자리를_반납한다() {
            assertThat(post(USER_BANNED).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            assertThat(permitsAvailable()).isEqualTo(1);
            assertThat(post(USER_OK).getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        private double permitsAvailable() {
            return meterRegistry.get("omp.order.sync.permits.available").gauge().value();
        }

        private double rejectedCount() {
            return meterRegistry.get("omp.order.sync.rejected").counter().count();
        }

        private ResponseEntity<String> post(long userId) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = "{\"ordererId\": " + userId + ", \"cartId\": " + CART_OK + ", \"shopId\": " + SHOP_OPEN + ", \"orderMenus\": []}";
            return rest.postForEntity("/api/v1/order", new HttpEntity<>(body, headers), String.class);
        }
    }
}
