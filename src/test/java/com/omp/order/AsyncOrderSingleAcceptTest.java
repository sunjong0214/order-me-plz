package com.omp.order;

import static com.omp.support.TestFixtures.CART_OK;
import static com.omp.support.TestFixtures.SHOP_OPEN;
import static com.omp.support.TestFixtures.USER_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.omp.order.async.AsyncOrderProcessor;
import com.omp.support.TestFixtures;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 비동기 SINGLE 구성의 접수는 DB를 쓰지 않는다: 검증 쿼리 없이 202를 주고, 워커의 "검증 + INSERT" 작업을 제출한다.
 * 워커는 mock이라 이 테스트의 쿼리 수는 접수 경로의 쿼리 수와 같다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "omp.order.async.transaction=single",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class AsyncOrderSingleAcceptTest {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory emf;
    @MockitoBean AsyncOrderProcessor asyncOrderProcessor;

    @BeforeEach
    void seed() {
        TestFixtures.resetAndSeed(jdbc);
    }

    @Test
    void 접수는_쿼리_없이_202를_주고_검증과_저장을_워커에_넘긴다() {
        when(asyncOrderProcessor.validateAndProcessOrderTask(any())).thenReturn(1L);
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        long queriesBefore = stats.getQueryExecutionCount();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"ordererId\": " + USER_OK + ", \"cartId\": " + CART_OK + ", \"shopId\": " + SHOP_OPEN + ", \"orderMenus\": []}";
        var res = rest.postForEntity("/api/v1/order/async", new HttpEntity<>(body, headers), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(asyncOrderProcessor, timeout(5000)).validateAndProcessOrderTask(any());
        verify(asyncOrderProcessor, never()).processOrderTask(any());
        assertThat(stats.getQueryExecutionCount()).isEqualTo(queriesBefore);
    }
}
