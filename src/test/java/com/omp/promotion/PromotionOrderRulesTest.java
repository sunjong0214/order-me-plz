package com.omp.promotion;

import static com.omp.support.TestFixtures.CART_OK;
import static com.omp.support.TestFixtures.SHOP_OPEN;
import static com.omp.support.TestFixtures.USER_BANNED;
import static com.omp.support.TestFixtures.USER_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omp.order.async.AsyncOrderManager;
import com.omp.order.async.OrderJobStatus;
import com.omp.support.TestFixtures;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 메모리 번호표 방식의 규칙과 보상(설계 3.4~3.5). "주문이 DB에 확실히 없으면 번호를 돌려준다"를 경우별로 확인한다.
 */
class PromotionOrderRulesTest {
    static final int STOCK = 10;
    static final long USER_TRIGGER = 999L;   // 참여 INSERT에서 DB 오류를 일으킬 사용자

    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
    @ActiveProfiles("test")
    @TestPropertySource(properties = {"omp.promotion.stock-mode=memory", "omp.promotion.reconcile-interval=1h"})
    abstract static class Base {
        @Autowired TestRestTemplate rest;
        @Autowired JdbcTemplate jdbc;
        @Autowired PromotionAdminService admin;
        @Autowired PromotionTicketAllocator allocator;
        @Autowired AsyncOrderManager asyncOrderManager;
        long promotionId;

        @BeforeEach
        void seed() {
            TestFixtures.resetAndSeed(jdbc);
            List<Object[]> users = new ArrayList<>();
            for (long u = 11; u <= 20; u++) {
                users.add(new Object[]{u, "u" + u + "@test.com", "u" + u, "GENERAL"});
            }
            users.add(new Object[]{USER_TRIGGER, "t@test.com", "t", "GENERAL"});
            jdbc.batchUpdate("INSERT INTO users(user_id, email, name, status) VALUES (?, ?, ?, ?)", users);
            promotionId = admin.create("test", STOCK, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));
        }

        @AfterEach
        void dropTrigger() {
            jdbc.execute("DROP TRIGGER IF EXISTS promotion_participation_fail");
        }

        ResponseEntity<String> post(String path, long userId, long promotion) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = "{\"ordererId\": " + userId + ", \"cartId\": " + CART_OK + ", \"shopId\": " + SHOP_OPEN
                    + ", \"orderMenus\": [], \"promotionId\": " + promotion + "}";
            return rest.postForEntity(path, new HttpEntity<>(body, headers), String.class);
        }

        ResponseEntity<String> postSync(long userId) {
            return post("/api/v1/order", userId, promotionId);
        }

        int remaining() {
            return allocator.remaining(promotionId);
        }

        int participations() {
            return TestFixtures.count(jdbc, "promotion_participations");
        }
    }

    @Nested
    class 동기 extends Base {
        @Test
        void 같은_사용자의_두_번째_주문은_409_ALREADY_JOINED() {
            assertThat(postSync(USER_OK).getStatusCode().value()).isEqualTo(200);

            ResponseEntity<String> second = postSync(USER_OK);

            assertThat(second.getStatusCode().value()).isEqualTo(409);
            assertThat(second.getBody()).contains("ALREADY_JOINED");
            assertThat(participations()).isEqualTo(1);
            assertThat(remaining()).isEqualTo(STOCK - 1);
        }

        @Test
        void 검증에_실패하면_롤백되고_번호를_돌려준다() {
            assertThat(postSync(USER_BANNED).getStatusCode().value()).isEqualTo(400);

            assertThat(remaining()).isEqualTo(STOCK);
            assertThat(participations()).isZero();
            assertThat(postSync(USER_OK).getStatusCode().value()).as("다른 사용자가 그 번호를 쓸 수 있다").isEqualTo(200);
        }

        @Test
        void 저장_중_DB_오류로_롤백되면_주문도_번호도_남지_않는다() {
            jdbc.execute("CREATE TRIGGER promotion_participation_fail BEFORE INSERT ON promotion_participations FOR EACH ROW "
                    + "BEGIN IF NEW.user_id = " + USER_TRIGGER + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'simulated'; END IF; END");

            assertThat(postSync(USER_TRIGGER).getStatusCode().value()).isEqualTo(500);

            assertThat(TestFixtures.count(jdbc, "orders")).as("주문 INSERT도 롤백").isZero();
            assertThat(participations()).isZero();
            assertThat(remaining()).isEqualTo(STOCK);
        }

        @Test
        void 기간_밖이면_409_NOT_OPEN이고_번호를_쓰지_않는다() {
            long later = admin.create("later", STOCK, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2));

            ResponseEntity<String> r = post("/api/v1/order", USER_OK, later);

            assertThat(r.getStatusCode().value()).isEqualTo(409);
            assertThat(r.getBody()).contains("NOT_OPEN");
            assertThat(allocator.remaining(later)).as("발급기를 열지도 않는다").isEqualTo(-1);
        }

        @Test
        void 없는_이벤트는_404() {
            assertThat(post("/api/v1/order", USER_OK, promotionId + 1000).getStatusCode().value()).isEqualTo(404);
            assertThat(rest.getForEntity("/api/v1/promotions/" + (promotionId + 1000) + "/check", String.class).getStatusCode().value())
                    .as("정합성 확인 API도 404").isEqualTo(404);
        }

        @Test
        void 매진되면_409_SOLD_OUT() {
            for (long u = 11; u <= 10 + STOCK; u++) {
                assertThat(postSync(u).getStatusCode().value()).isEqualTo(200);
            }
            ResponseEntity<String> r = postSync(USER_OK);

            assertThat(r.getStatusCode().value()).isEqualTo(409);
            assertThat(r.getBody()).contains("SOLD_OUT");
            assertThat(participations()).isEqualTo(STOCK);
        }

        @Test
        void DB_제약이_번호_중복과_범위_초과를_막는다() {
            assertThat(postSync(USER_OK).getStatusCode().value()).isEqualTo(200);
            Integer used = jdbc.queryForObject("SELECT ticket_no FROM promotion_participations", Integer.class);

            assertThatThrownBy(() -> jdbc.update("INSERT INTO promotion_participations (promotion_id, user_id, order_id, ticket_no, created_at) "
                    + "VALUES (?, ?, 1, ?, NOW())", promotionId, 11L, used))
                    .as("같은 번호").isInstanceOf(DuplicateKeyException.class);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO promotion_participations (promotion_id, user_id, order_id, ticket_no, created_at) "
                    + "VALUES (?, ?, 1, ?, NOW())", promotionId, 12L, STOCK + 1))
                    .as("범위 밖 번호(외래 키)").isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void 재시작하면_DB에서_쓰인_번호와_참여자를_다시_읽는다() {
            for (long u = 11; u <= 13; u++) {
                assertThat(postSync(u).getStatusCode().value()).isEqualTo(200);
            }
            allocator.reload(promotionId);   // 재시작과 같다: 메모리 상태를 버린다

            assertThat(postSync(11L).getStatusCode().value()).as("이미 참여").isEqualTo(409);
            assertThat(postSync(14L).getStatusCode().value()).isEqualTo(200);

            assertThat(remaining()).isEqualTo(STOCK - 4);
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT ticket_no) FROM promotion_participations", Integer.class)).isEqualTo(4);
        }
    }

    @Nested
    class 비동기 extends Base {
        @Test
        void 워커에서_검증에_실패하면_FAILED이고_번호를_돌려준다() {
            ResponseEntity<String> r = post("/api/v1/order/async", USER_BANNED, promotionId);
            assertThat(r.getStatusCode().value()).isEqualTo(202);
            String uuid = r.getHeaders().getLocation().getPath().replace("/api/v1/order/sse/", "");

            assertThat(TestFixtures.awaitUntil(() -> asyncOrderManager.getJobState(uuid).status() == OrderJobStatus.FAILED,
                    Duration.ofSeconds(5))).isTrue();
            assertThat(TestFixtures.awaitUntil(() -> remaining() == STOCK, Duration.ofSeconds(5))).isTrue();
            assertThat(participations()).isZero();
        }

        @Test
        void 매진은_접수에서_바로_409() {
            for (long u = 11; u <= 10 + STOCK; u++) {
                assertThat(post("/api/v1/order/async", u, promotionId).getStatusCode().value()).isEqualTo(202);
            }
            ResponseEntity<String> r = post("/api/v1/order/async", USER_OK, promotionId);

            assertThat(r.getStatusCode().value()).isEqualTo(409);
            assertThat(r.getBody()).contains("SOLD_OUT");
            assertThat(TestFixtures.awaitUntil(() -> participations() == STOCK, Duration.ofSeconds(5))).isTrue();
        }
    }

    @Nested
    @TestPropertySource(properties = {"omp.executor.insert.core=1", "omp.executor.insert.max=1", "omp.executor.insert.queue=1"})
    class 대기_자리_없음 extends Base {
        @Autowired @Qualifier("insertTaskExecutor") Executor insertTaskExecutor;

        @Test
        void 대기_자리가_없으면_429이고_번호를_쓰지_않는다() throws Exception {
            CountDownLatch hold = new CountDownLatch(1);
            insertTaskExecutor.execute(() -> {
                try {
                    hold.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            try {
                assertThat(post("/api/v1/order/async", 11L, promotionId).getStatusCode().value()).isEqualTo(202);   // 큐 한 칸
                ResponseEntity<String> overflow = post("/api/v1/order/async", 12L, promotionId);

                assertThat(overflow.getStatusCode().value()).isEqualTo(429);
                assertThat(remaining()).as("큐에 있는 한 건만 번호를 쥔다").isEqualTo(STOCK - 1);
            } finally {
                hold.countDown();
            }
            assertThat(TestFixtures.awaitUntil(() -> participations() == 1, Duration.ofSeconds(5))).isTrue();
        }
    }
}
