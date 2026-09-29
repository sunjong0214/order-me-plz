package com.omp.promotion;

import static com.omp.support.TestFixtures.SHOP_OPEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.omp.order.async.AsyncOrderManager;
import com.omp.order.async.OrderJobState;
import com.omp.order.async.OrderJobStatus;
import com.omp.support.TestFixtures;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 동시 폭주에서의 정합성(설계 5장): 재고 100개에 서로 다른 사용자 300명이 동시에 주문한다.
 * 세 재고 방식 × 동기·비동기 모두 성공 정확히 100건, 초과 판매 0, 나머지는 매진이어야 한다.
 * - 동기: 200 × 100 + 409 SOLD_OUT × 200
 * - 비동기 MEMORY: 202 × 100 + 접수에서 409 SOLD_OUT × 200
 * - 비동기 DB 방식: 202 × 300, 워커에서 100건 COMPLETED + 200건 FAILED(SOLD_OUT)
 */
class PromotionOrderConcurrencyTest {
    static final int STOCK = 100;
    static final int USERS = 300;
    static final long FIRST_USER = 1001;

    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
    @ActiveProfiles("test")
    @TestPropertySource(properties = "omp.executor.insert.queue=1000")   // 300건이 모두 대기 자리에 들어가게
    abstract static class Base {
        @Autowired TestRestTemplate rest;
        @Autowired JdbcTemplate jdbc;
        @Autowired PromotionAdminService admin;
        @Autowired AsyncOrderManager asyncOrderManager;
        long promotionId;

        @BeforeEach
        void seed() {
            TestFixtures.resetAndSeed(jdbc);
            List<Object[]> users = new ArrayList<>();
            for (long u = FIRST_USER; u < FIRST_USER + USERS; u++) {
                users.add(new Object[]{u, "u" + u + "@test.com", "u" + u, "GENERAL"});
            }
            jdbc.batchUpdate("INSERT INTO users(user_id, email, name, status) VALUES (?, ?, ?, ?)", users);
            List<Object[]> carts = users.stream().map(u -> new Object[]{u[0], u[0], SHOP_OPEN}).toList();
            jdbc.batchUpdate("INSERT INTO carts(cart_id, user_id, shop_id) VALUES (?, ?, ?)", carts);
            promotionId = admin.create("test", STOCK, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));
        }

        @Test
        void 동기_폭주에서_재고만큼만_성공한다() throws Exception {
            List<ResponseEntity<String>> responses = fire("/api/v1/order");

            Map<Integer, Long> byStatus = countBy(responses, r -> r.getStatusCode().value());
            printDeadlockIfFailed(byStatus);
            assertThat(byStatus).containsEntry(200, (long) STOCK).containsEntry(409, (long) (USERS - STOCK));
            assertThat(responses.stream().filter(r -> r.getStatusCode().value() == 409))
                    .allMatch(r -> r.getBody() != null && r.getBody().contains("SOLD_OUT"));
            assertConsistent();
        }

        @Test
        void 비동기_폭주에서_재고만큼만_저장된다() throws Exception {
            List<ResponseEntity<String>> responses = fire("/api/v1/order/async");
            List<String> uuids = responses.stream().filter(r -> r.getStatusCode().value() == 202)
                    .map(r -> r.getHeaders().getLocation().getPath().replace("/api/v1/order/sse/", "")).toList();

            assertThat(TestFixtures.awaitUntil(() -> uuids.stream().allMatch(u -> state(u).status() != OrderJobStatus.PROCESSING),
                    Duration.ofSeconds(30))).as("모든 접수 건이 끝난다").isTrue();
            Map<OrderJobStatus, Long> byState = countBy(uuids, u -> state(u).status());
            long rejectedAtAccept = responses.stream().filter(r -> r.getStatusCode().value() == 409).count();

            assertThat(byState.getOrDefault(OrderJobStatus.COMPLETED, 0L)).isEqualTo(STOCK);
            assertThat(byState.getOrDefault(OrderJobStatus.FAILED, 0L) + rejectedAtAccept).isEqualTo(USERS - STOCK);
            assertThat(uuids.stream().map(this::state).filter(s -> s.status() == OrderJobStatus.FAILED))
                    .allMatch(s -> "SOLD_OUT".equals(s.failureCode()));
            assertAcceptBehavior(uuids.size(), rejectedAtAccept);
            assertConsistent();
        }

        /** 방식마다 매진을 어디서 알리는지 확인한다. */
        abstract void assertAcceptBehavior(int accepted, long rejectedAtAccept);

        void assertConsistent() {
            assertThat(TestFixtures.count(jdbc, "promotion_participations")).as("참여 행 = 재고").isEqualTo(STOCK);
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT user_id) FROM promotion_participations", Integer.class)).isEqualTo(STOCK);
            assertThat(TestFixtures.count(jdbc, "orders")).as("할인 주문 수 = 재고").isEqualTo(STOCK);
        }

        /** 예상 밖 실패(500)가 있으면 원인 분석용으로 MySQL의 최근 교착 기록을 남긴다. */
        void printDeadlockIfFailed(Map<Integer, Long> byStatus) {
            if (!byStatus.containsKey(500)) {
                return;
            }
            String status = jdbc.queryForObject("SHOW ENGINE INNODB STATUS", (rs, i) -> rs.getString("Status"));
            int from = status == null ? -1 : status.indexOf("LATEST DETECTED DEADLOCK");
            int to = status == null ? -1 : status.indexOf("TRANSACTIONS", from + 1);
            System.out.println("[deadlock] " + (from < 0 ? "기록 없음" : status.substring(from, to < 0 ? status.length() : to)));
        }

        OrderJobState state(String uuid) {
            return asyncOrderManager.getJobState(uuid);
        }

        List<ResponseEntity<String>> fire(String path) throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(32);
            CountDownLatch start = new CountDownLatch(1);
            List<ResponseEntity<String>> responses = new ArrayList<>();
            Map<Long, ResponseEntity<String>> byUser = new ConcurrentHashMap<>();
            for (long u = FIRST_USER; u < FIRST_USER + USERS; u++) {
                long userId = u;
                pool.submit(() -> {
                    start.await();
                    byUser.put(userId, post(path, userId));
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
            responses.addAll(byUser.values());
            assertThat(responses).hasSize(USERS);
            return responses;
        }

        ResponseEntity<String> post(String path, long userId) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = "{\"ordererId\": " + userId + ", \"cartId\": " + userId + ", \"shopId\": " + SHOP_OPEN
                    + ", \"orderMenus\": [], \"promotionId\": " + promotionId + "}";
            return rest.postForEntity(path, new HttpEntity<>(body, headers), String.class);
        }

        static <T, K> Map<K, Long> countBy(List<T> items, Function<T, K> key) {
            return items.stream().collect(Collectors.groupingBy(key, Collectors.counting()));
        }
    }

    @Nested
    @TestPropertySource(properties = "omp.promotion.stock-mode=db-single")
    class DB_행_하나 extends Base {
        @Override
        void assertAcceptBehavior(int accepted, long rejectedAtAccept) {
            assertThat(accepted).as("접수는 DB 없이 전부 202").isEqualTo(USERS);
            assertThat(jdbc.queryForObject("SELECT remaining_stock FROM promotions WHERE promotion_id = ?", Integer.class, promotionId)).isZero();
        }
    }

    @Nested
    @TestPropertySource(properties = "omp.promotion.stock-mode=db-bucket")
    class DB_여러_행 extends Base {
        @Override
        void assertAcceptBehavior(int accepted, long rejectedAtAccept) {
            assertThat(accepted).as("접수는 DB 없이 전부 202").isEqualTo(USERS);
            assertThat(jdbc.queryForObject("SELECT SUM(remaining) FROM promotion_stock_buckets WHERE promotion_id = ?", Integer.class, promotionId)).isZero();
        }
    }

    @Nested
    @TestPropertySource(properties = "omp.promotion.stock-mode=memory")
    class 메모리_번호표 extends Base {
        @Override
        void assertAcceptBehavior(int accepted, long rejectedAtAccept) {
            assertThat(accepted).as("번호표를 받은 만큼만 202").isEqualTo(STOCK);
            assertThat(rejectedAtAccept).as("매진은 접수에서 바로 409").isEqualTo(USERS - STOCK);
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT ticket_no) FROM promotion_participations", Integer.class)).isEqualTo(STOCK);
            assertThat(jdbc.queryForObject("SELECT MAX(ticket_no) FROM promotion_participations", Integer.class)).isLessThanOrEqualTo(STOCK);
        }

        @Test
        void 동기에서도_번호표가_겹치지_않는다() throws Exception {
            fire("/api/v1/order");
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT ticket_no) FROM promotion_participations", Integer.class)).isEqualTo(STOCK);
        }
    }
}
