package com.omp.order;

import static com.omp.support.TestFixtures.CART_OK;
import static com.omp.support.TestFixtures.SHOP_OPEN;
import static com.omp.support.TestFixtures.USER_OK;
import static org.assertj.core.api.Assertions.assertThat;

import com.omp.support.TestFixtures;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 실제 HTTP로 폴링 계약을 검증한다: 처리 중 202 + PROCESSING JSON, 완료 후 303 + COMPLETED JSON + orderId, 모르는 uuid 404.
 * (메모리 상태 테스트만으로는 JSON 직렬화 실패를 잡지 못했다.) 리다이렉트를 따라가지 않도록 JDK HttpClient를 직접 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "omp.executor.insert.core=1",
        "omp.executor.insert.max=1",
        "omp.executor.insert.queue=10"
})
class AsyncOrderStatusHttpTest {

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired @Qualifier("insertTaskExecutor") Executor insertTaskExecutor;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @BeforeEach
    void seed() {
        TestFixtures.resetAndSeed(jdbc);
    }

    @Test
    void 처리_중_202_완료_후_303과_orderId_JSON() throws Exception {
        CountDownLatch hold = new CountDownLatch(1);
        insertTaskExecutor.execute(() -> awaitQuietly(hold));

        String uuid;
        try {
            HttpResponse<String> accepted = send(HttpRequest.newBuilder(uri("/api/v1/order/async"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"ordererId\": " + USER_OK + ", \"cartId\": " + CART_OK + ", \"shopId\": " + SHOP_OPEN + ", \"orderMenus\": []}"))
                    .build());
            assertThat(accepted.statusCode()).isEqualTo(202);
            String location = accepted.headers().firstValue("Location").orElseThrow();
            uuid = location.substring(location.lastIndexOf('/') + 1);

            HttpResponse<String> processing = send(HttpRequest.newBuilder(uri("/api/v1/order/async/" + uuid)).GET().build());
            assertThat(processing.statusCode()).isEqualTo(202);
            assertThat(processing.body()).contains("\"status\":\"PROCESSING\"");
        } finally {
            hold.countDown();
        }

        final String id = uuid;
        boolean done = TestFixtures.awaitUntil(() -> statusOf(id) == 303, Duration.ofSeconds(5));
        assertThat(done).isTrue();

        HttpResponse<String> completed = send(HttpRequest.newBuilder(uri("/api/v1/order/async/" + uuid)).GET().build());
        Long orderId = jdbc.queryForObject("SELECT order_id FROM orders", Long.class);
        assertThat(completed.body()).contains("\"status\":\"COMPLETED\"").contains("\"orderId\":" + orderId);
        assertThat(completed.headers().firstValue("Location")).contains("/api/v1/order/" + orderId);
    }

    @Test
    void 모르는_uuid는_404() throws Exception {
        assertThat(statusOf("nope")).isEqualTo(404);
    }

    private int statusOf(String uuid) {
        try {
            return send(HttpRequest.newBuilder(uri("/api/v1/order/async/" + uuid)).GET().build()).statusCode();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HttpResponse<String> send(HttpRequest req) throws Exception {
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
