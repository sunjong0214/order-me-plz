package com.omp.order;

import static com.omp.support.TestFixtures.CART_OK;
import static com.omp.support.TestFixtures.SHOP_OPEN;
import static com.omp.support.TestFixtures.USER_OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.omp.order.async.AsyncOrderProcessor;
import com.omp.support.TestFixtures;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 대기 자리가 없을 때의 응답 코드(omp.order.async.reject-status)와 연결 처리.
 * Tomcat은 503 응답에 "Connection: close"를 붙이고 연결을 닫으며, 429는 연결을 유지한다.
 * 과부하에서 거절이 많을 때 503은 거절마다 새 TCP 연결을 만들게 해 기본값을 429로 두었다(benchmark/results/2026-09-26-P2 7절).
 * 헤더는 클라이언트 라이브러리가 걸러 내지 않도록 소켓으로 직접 읽는다.
 */
class AsyncOrderRejectStatusTest {

    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
            "omp.executor.insert.core=1",
            "omp.executor.insert.max=1",
            "omp.executor.insert.queue=1"
    })
    abstract static class Base {
        @LocalServerPort int port;
        @Autowired TestRestTemplate rest;
        @Autowired JdbcTemplate jdbc;
        @Autowired MeterRegistry meterRegistry;
        @MockitoBean AsyncOrderProcessor asyncOrderProcessor;

        @BeforeEach
        void seed() {
            TestFixtures.resetAndSeed(jdbc);
        }

        /** 워커 1개와 대기 자리 1개를 채운 뒤 세 번째 요청의 응답 헤더를 돌려준다. */
        String rejectedResponseHead() throws Exception {
            CountDownLatch hold = new CountDownLatch(1);
            when(asyncOrderProcessor.processOrderTask(any())).thenAnswer(inv -> {
                hold.await(10, TimeUnit.SECONDS);
                return 1L;
            });
            try {
                assertThat(post().getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
                assertThat(TestFixtures.awaitUntil(() -> slotsAvailable() == 1, Duration.ofSeconds(5))).isTrue();
                assertThat(post().getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
                return rawPostHead();
            } finally {
                hold.countDown();
            }
        }

        private double slotsAvailable() {
            return meterRegistry.get("omp.order.async.slots.available").gauge().value();
        }

        private org.springframework.http.ResponseEntity<String> post() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return rest.postForEntity("/api/v1/order/async", new HttpEntity<>(body(), headers), String.class);
        }

        private String rawPostHead() throws IOException {
            byte[] b = body().getBytes(UTF_8);
            try (Socket s = new Socket("localhost", port)) {
                s.setSoTimeout(5000);
                OutputStream out = s.getOutputStream();
                out.write(("POST /api/v1/order/async HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\n"
                        + "Content-Length: " + b.length + "\r\n\r\n").getBytes(UTF_8));
                out.write(b);
                out.flush();
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), UTF_8));
                StringBuilder head = new StringBuilder();
                for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
                    head.append(line).append('\n');
                }
                return head.toString().toLowerCase(Locale.ROOT);
            }
        }

        private static String body() {
            return "{\"ordererId\": " + USER_OK + ", \"cartId\": " + CART_OK + ", \"shopId\": " + SHOP_OPEN + ", \"orderMenus\": []}";
        }
    }

    @Nested
    class 기본값_429 extends Base {
        @Test
        void 거절은_429와_Retry_After이고_연결을_유지한다() throws Exception {
            String head = rejectedResponseHead();

            assertThat(head).startsWith("http/1.1 429");
            assertThat(head).contains("retry-after:");
            assertThat(head).doesNotContain("connection: close");
        }
    }

    @Nested
    @TestPropertySource(properties = "omp.order.async.reject-status=503")
    class 설정_503 extends Base {
        @Test
        void 거절은_503과_Retry_After이고_Tomcat이_연결을_닫는다() throws Exception {
            String head = rejectedResponseHead();

            assertThat(head).startsWith("http/1.1 503");
            assertThat(head).contains("retry-after:");
            assertThat(head).contains("connection: close");
        }
    }
}
