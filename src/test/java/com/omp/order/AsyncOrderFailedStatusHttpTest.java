package com.omp.order;

import static com.omp.support.TestFixtures.CART_OK;
import static com.omp.support.TestFixtures.SHOP_OPEN;
import static com.omp.support.TestFixtures.USER_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.omp.order.async.AsyncOrderProcessor;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 저장(INSERT) 실패 시 폴링 계약: 200 + FAILED 상태 JSON (이전에는 의미가 맞지 않는 406).
 * 저장 단계를 강제로 실패시키고, 실패 카운터(omp.order.async.failed)가 오르며 주문 행이 남지 않는지도 확인한다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AsyncOrderFailedStatusHttpTest {

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeterRegistry meterRegistry;
    @MockitoBean AsyncOrderProcessor asyncOrderProcessor;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @BeforeEach
    void seed() {
        TestFixtures.resetAndSeed(jdbc);
    }

    @Test
    void 저장_실패는_200과_FAILED_상태로_조회되고_실패_카운터가_오른다() throws Exception {
        when(asyncOrderProcessor.processOrderTask(any())).thenThrow(new IllegalStateException("forced insert failure"));
        double failedBefore = failedCount();

        HttpResponse<String> accepted = send(HttpRequest.newBuilder(uri("/api/v1/order/async"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"ordererId\": " + USER_OK + ", \"cartId\": " + CART_OK + ", \"shopId\": " + SHOP_OPEN + ", \"orderMenus\": []}"))
                .build());
        assertThat(accepted.statusCode()).isEqualTo(202);
        String location = accepted.headers().firstValue("Location").orElseThrow();
        String uuid = location.substring(location.lastIndexOf('/') + 1);

        boolean failed = TestFixtures.awaitUntil(() -> failedCount() - failedBefore == 1.0, Duration.ofSeconds(5));
        assertThat(failed).isTrue();

        HttpResponse<String> status = send(HttpRequest.newBuilder(uri("/api/v1/order/async/" + uuid)).GET().build());
        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(status.body()).contains("\"status\":\"FAILED\"");
        assertThat(TestFixtures.count(jdbc, "orders")).isZero();
    }

    private double failedCount() {
        var counter = meterRegistry.find("omp.order.async.failed").counter();
        return counter == null ? 0.0 : counter.count();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
