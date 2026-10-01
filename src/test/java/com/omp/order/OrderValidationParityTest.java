package com.omp.order;

import static com.omp.support.TestFixtures.CART_BANNED;
import static com.omp.support.TestFixtures.CART_OK;
import static com.omp.support.TestFixtures.CART_OTHER;
import static com.omp.support.TestFixtures.SHOP_CLOSED;
import static com.omp.support.TestFixtures.SHOP_OPEN;
import static com.omp.support.TestFixtures.USER_BANNED;
import static com.omp.support.TestFixtures.USER_OK;
import static com.omp.support.TestFixtures.USER_OTHER;
import static org.assertj.core.api.Assertions.assertThat;

import com.omp.support.TestFixtures;
import java.time.Duration;
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

/**
 * 동기·비동기 주문은 "실행 방식"만 달라야 한다. 검증 규칙과 응답 코드가 같아야 전/후 비교가 성립한다.
 * 기존: 동기는 세 조건 중 하나만 유효해도 통과(||), 비동기는 존재 여부만 3회 조회. 둘 다 실패 시 500.
 * 비동기는 테스트 프로필의 SPLIT 구성이라 접수에서 바로 400이다. SINGLE 구성(202 뒤 FAILED)은 AsyncOrderSingleTransactionTest.
 * 차단·영업 종료·장바구니 소유 케이스는 그 조건 하나만 어긋나게 한다(나머지는 주문자 자기 장바구니로, 그 장바구니의 가게에 주문).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderValidationParityTest {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        TestFixtures.resetAndSeed(jdbc);
    }

    @Test
    void 유효한_주문은_두_경로_모두_저장된다() {
        assertThat(post("/api/v1/order", USER_OK, CART_OK, SHOP_OPEN).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/api/v1/order/async", USER_OK, CART_OK, SHOP_OPEN).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        assertThat(TestFixtures.awaitUntil(() -> TestFixtures.count(jdbc, "orders") == 2, Duration.ofSeconds(5))).isTrue();
    }

    @Test
    void 차단된_사용자는_두_경로_모두_400이다() {
        assertBothReject(USER_BANNED, CART_BANNED, SHOP_OPEN);
    }

    @Test
    void 영업_종료_가게는_두_경로_모두_400이다() {
        assertBothReject(USER_OTHER, CART_OTHER, SHOP_CLOSED);
    }

    @Test
    void 다른_가게의_장바구니는_두_경로_모두_400이다() {
        assertBothReject(USER_OTHER, CART_OTHER, SHOP_OPEN);   // 자기 장바구니지만 SHOP_CLOSED의 것
    }

    @Test
    void 남의_장바구니는_두_경로_모두_400이다() {
        assertBothReject(USER_OTHER, CART_OK, SHOP_OPEN);      // USER_OK의 장바구니
    }

    @Test
    void 존재하지_않는_장바구니는_두_경로_모두_400이다() {
        assertBothReject(USER_OK, 999L, SHOP_OPEN);
    }

    @Test
    void 존재하지_않는_사용자는_두_경로_모두_400이다() {
        assertBothReject(999L, CART_OK, SHOP_OPEN);
    }

    private void assertBothReject(long ordererId, long cartId, long shopId) {
        assertThat(post("/api/v1/order", ordererId, cartId, shopId).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/api/v1/order/async", ordererId, cartId, shopId).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TestFixtures.count(jdbc, "orders")).isZero();
    }

    private ResponseEntity<String> post(String path, long ordererId, long cartId, long shopId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"ordererId\": " + ordererId + ", \"cartId\": " + cartId
                + ", \"shopId\": " + shopId + ", \"orderMenus\": []}";
        return rest.postForEntity(path, new HttpEntity<>(body, headers), String.class);
    }
}
