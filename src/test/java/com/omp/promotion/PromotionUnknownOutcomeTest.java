package com.omp.promotion;

import static com.omp.support.TestFixtures.CART_OK;
import static com.omp.support.TestFixtures.SHOP_OPEN;
import static com.omp.support.TestFixtures.USER_OK;
import static org.assertj.core.api.Assertions.assertThat;

import com.omp.support.TestFixtures;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * 커밋 결과를 모를 때(설계 3.4 "결과 모름"). 커밋 호출이 예외로 끝나면 Spring은 STATUS_UNKNOWN을 알린다(spring-tx 6.2.1 확인).
 * 이때 번호를 바로 돌려주지 않고, 대조기가 (이벤트, 사용자) 참여 행이 있는지 보고 정한다.
 * - 실제로는 커밋됐는데 응답만 잃은 경우: 참여 행이 있으므로 확정(번호를 돌려주면 초과 판매가 된다)
 * - 실제로는 커밋되지 않은 경우: 참여 행이 없으므로 반납
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {"omp.promotion.stock-mode=memory", "omp.promotion.reconcile-interval=1h"})
class PromotionUnknownOutcomeTest {
    static final int STOCK = 10;

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired PromotionAdminService admin;
    @Autowired PromotionTicketAllocator allocator;
    long promotionId;

    @TestConfiguration
    static class FaultyCommitConfig {
        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory emf) {
            return new FaultyJpaTransactionManager(emf);
        }
    }

    /** 다음 커밋 한 번에만 장애를 흉내 낸다. */
    static class FaultyJpaTransactionManager extends JpaTransactionManager {
        enum Fault { NONE, COMMIT_THEN_THROW, THROW_WITHOUT_COMMIT }

        static final AtomicReference<Fault> NEXT = new AtomicReference<>(Fault.NONE);

        FaultyJpaTransactionManager(EntityManagerFactory emf) {
            super(emf);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            Fault fault = NEXT.getAndSet(Fault.NONE);
            if (fault == Fault.THROW_WITHOUT_COMMIT) {
                // 커밋이 DB에 닿지 못하면(연결 끊김 등) DB가 트랜잭션을 되돌린다. 롤백 없이 예외만 던지면 Hibernate가 JPA 규칙대로
                // 진행 중인 트랜잭션의 EntityManager 종료를 미뤄 잠금을 쥔 연결이 남는다(현실과 다른 흉내). 그래서 먼저 되돌린다.
                super.doRollback(status);
                throw new TransactionSystemException("simulated: commit did not reach the database");
            }
            super.doCommit(status);
            if (fault == Fault.COMMIT_THEN_THROW) {
                throw new TransactionSystemException("simulated: commit acknowledgement lost");
            }
        }
    }

    @BeforeEach
    void seed() {
        TestFixtures.resetAndSeed(jdbc);
        promotionId = admin.create("test", STOCK, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));
    }

    @AfterEach
    void clearFault() {
        FaultyJpaTransactionManager.NEXT.set(FaultyJpaTransactionManager.Fault.NONE);
    }

    @Test
    void 커밋됐는데_결과를_모르면_DB에서_확인하고_확정한다() {
        FaultyJpaTransactionManager.NEXT.set(FaultyJpaTransactionManager.Fault.COMMIT_THEN_THROW);

        assertThat(order().getStatusCode().value()).isEqualTo(500);
        assertThat(allocator.pendingCount()).as("확인 대기").isEqualTo(1);
        assertThat(allocator.remaining(promotionId)).as("확인 전에는 돌려주지 않는다").isEqualTo(STOCK - 1);
        assertThat(TestFixtures.count(jdbc, "promotion_participations")).as("실제로는 커밋됨").isEqualTo(1);

        allocator.reconcileNow();

        assertThat(allocator.pendingCount()).isZero();
        assertThat(allocator.remaining(promotionId)).as("확정: 번호를 돌려주지 않는다").isEqualTo(STOCK - 1);
        assertThat(order().getStatusCode().value()).as("같은 사용자는 이미 참여").isEqualTo(409);
    }

    @Test
    void 커밋되지_않았는데_결과를_모르면_DB에서_확인하고_돌려준다() {
        FaultyJpaTransactionManager.NEXT.set(FaultyJpaTransactionManager.Fault.THROW_WITHOUT_COMMIT);

        assertThat(order().getStatusCode().value()).isEqualTo(500);
        assertThat(allocator.pendingCount()).isEqualTo(1);
        assertThat(TestFixtures.count(jdbc, "promotion_participations")).as("실제로는 커밋 안 됨").isZero();

        allocator.reconcileNow();

        assertThat(allocator.pendingCount()).isZero();
        assertThat(allocator.remaining(promotionId)).as("반납").isEqualTo(STOCK);
        assertThat(order().getStatusCode().value()).as("같은 사용자가 다시 주문할 수 있다").isEqualTo(200);
    }

    private org.springframework.http.ResponseEntity<String> order() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"ordererId\": " + USER_OK + ", \"cartId\": " + CART_OK + ", \"shopId\": " + SHOP_OPEN
                + ", \"orderMenus\": [], \"promotionId\": " + promotionId + "}";
        return rest.postForEntity("/api/v1/order", new HttpEntity<>(body, headers), String.class);
    }
}
