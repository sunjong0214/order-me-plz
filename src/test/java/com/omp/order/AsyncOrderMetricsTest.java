package com.omp.order;

import static com.omp.support.TestFixtures.CART_OK;
import static com.omp.support.TestFixtures.SHOP_OPEN;
import static com.omp.support.TestFixtures.USER_OK;
import static org.assertj.core.api.Assertions.assertThat;

import com.omp.support.TestFixtures;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 벤치마크 계측 검증: 비동기 주문의 큐 대기·저장 완료 Timer가 기록되고,
 * /actuator/prometheus 에 누적 히스토그램 버킷(le="30.0" 등)으로 노출되며,
 * 폴링 스크립트가 쓰는 /actuator/metrics 와 Tomcat 스레드 지표도 그대로 조회되는지 확인한다.
 * Spring Boot 테스트는 기본적으로 메트릭 내보내기(Prometheus 레지스트리)를 끄므로, 실제 서버와 같게 켠다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureObservability(tracing = false)
class AsyncOrderMetricsTest {

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeterRegistry meterRegistry;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void seed() {
        TestFixtures.resetAndSeed(jdbc);
    }

    @Test
    void 저장_완료와_큐_대기가_기록되고_누적_히스토그램으로_노출된다() throws Exception {
        long completedBefore = completedTimer().count();
        long queueWaitBefore = meterRegistry.get("omp.order.async.queue.wait").timer().count();

        HttpResponse<String> accepted = send(HttpRequest.newBuilder(uri("/api/v1/order/async"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"ordererId\": " + USER_OK + ", \"cartId\": " + CART_OK + ", \"shopId\": " + SHOP_OPEN + ", \"orderMenus\": []}"))
                .build());
        assertThat(accepted.statusCode()).isEqualTo(202);

        boolean recorded = TestFixtures.awaitUntil(
                () -> completedTimer().count() - completedBefore == 1, Duration.ofSeconds(5));
        assertThat(recorded).isTrue();
        assertThat(meterRegistry.get("omp.order.async.queue.wait").timer().count() - queueWaitBefore).isEqualTo(1);

        HttpResponse<String> promRes = send(HttpRequest.newBuilder(uri("/actuator/prometheus")).GET().build());
        assertThat(promRes.statusCode()).isEqualTo(200);
        String prometheus = promRes.body();
        double le30 = bucket(prometheus, "omp_order_async_completion_seconds_bucket", "outcome=\"completed\"", "le=\"30.0\"").orElseThrow();
        double total = bucket(prometheus, "omp_order_async_completion_seconds_count", "outcome=\"completed\"", "").orElseThrow();
        assertThat(le30).isGreaterThanOrEqualTo(1.0);
        assertThat(le30).isEqualTo(total);   // 누적 버킷: 테스트의 저장은 모두 30초 안에 끝나므로 le=30 = 전체 건수
        assertThat(prometheus).contains("omp_order_async_queue_wait_seconds_bucket");
        assertThat(prometheus).contains("tomcat_threads_busy_threads");

        HttpResponse<String> queued = send(HttpRequest.newBuilder(
                uri("/actuator/metrics/executor.queued?tag=name:insertTaskExecutor")).GET().build());
        assertThat(queued.statusCode()).isEqualTo(200);
        assertThat(queued.body()).contains("\"value\"");
    }

    private Timer completedTimer() {
        return meterRegistry.get("omp.order.async.completion").tag("outcome", "completed").timer();
    }

    /** Prometheus 텍스트에서 이름으로 시작하고 두 라벨 조각을 모두 포함하는 줄의 값을 읽는다(라벨 순서 무관). */
    private static Optional<Double> bucket(String text, String name, String label1, String label2) {
        return text.lines()
                .filter(l -> l.startsWith(name + "{") && l.contains(label1) && l.contains(label2))
                .map(l -> Double.parseDouble(l.substring(l.lastIndexOf(' ') + 1)))
                .findFirst();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
