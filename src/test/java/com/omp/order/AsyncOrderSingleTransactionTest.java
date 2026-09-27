package com.omp.order;

import static com.omp.support.TestFixtures.CART_OK;
import static com.omp.support.TestFixtures.SHOP_OPEN;
import static com.omp.support.TestFixtures.USER_BANNED;
import static com.omp.support.TestFixtures.USER_OK;
import static org.assertj.core.api.Assertions.assertThat;

import com.omp.support.TestFixtures;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 비동기 SINGLE 구성: 검증과 INSERT가 워커 트랜잭션 하나에서 일어난다.
 * 유효한 주문은 저장되고, 잘못된 주문은 접수(202) 뒤 상태 조회에서 FAILED가 되며 행이 남지 않는다(SPLIT은 요청 즉시 400).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "omp.order.async.transaction=single")
class AsyncOrderSingleTransactionTest {

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeterRegistry meterRegistry;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @BeforeEach
    void seed() {
        TestFixtures.resetAndSeed(jdbc);
    }

    @Test
    void 유효한_주문은_접수_후_저장되고_완료로_조회된다() throws Exception {
        String uuid = acceptedUuid(USER_OK);

        assertThat(TestFixtures.awaitUntil(() -> TestFixtures.count(jdbc, "orders") == 1, Duration.ofSeconds(5))).isTrue();
        assertThat(TestFixtures.awaitUntil(() -> status(uuid).statusCode() == 303, Duration.ofSeconds(5))).isTrue();
    }

    @Test
    void 잘못된_주문도_접수되고_이후_FAILED로_조회된다() throws Exception {
        double failedBefore = failedCount();

        String uuid = acceptedUuid(USER_BANNED);

        assertThat(TestFixtures.awaitUntil(() -> failedCount() - failedBefore == 1.0, Duration.ofSeconds(5))).isTrue();
        HttpResponse<String> status = status(uuid);
        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(status.body()).contains("\"status\":\"FAILED\"");
        assertThat(TestFixtures.count(jdbc, "orders")).isZero();
    }

    private String acceptedUuid(long ordererId) throws Exception {
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(uri("/api/v1/order/async"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"ordererId\": " + ordererId + ", \"cartId\": " + CART_OK + ", \"shopId\": " + SHOP_OPEN + ", \"orderMenus\": []}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(202);
        String location = res.headers().firstValue("Location").orElseThrow();
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private HttpResponse<String> status(String uuid) {
        try {
            return http.send(HttpRequest.newBuilder(uri("/api/v1/order/async/" + uuid)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private double failedCount() {
        var counter = meterRegistry.find("omp.order.async.failed").counter();
        return counter == null ? 0.0 : counter.count();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
