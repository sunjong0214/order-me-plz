package com.omp.support;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.springframework.jdbc.core.JdbcTemplate;

/** 통합 테스트용 최소 마스터 데이터. 엔티티 생성자 제약(Shop은 isOpen=false 고정)을 피해 SQL로 직접 넣는다. */
public final class TestFixtures {
    public static final long USER_OK = 1L;
    public static final long USER_BANNED = 2L;
    public static final long SHOP_OPEN = 1L;
    public static final long SHOP_CLOSED = 2L;
    public static final long CART_OK = 1L;

    /** 통합 테스트가 붙어야 하는 스키마. application-test.properties 와 일치해야 한다. */
    public static final String TEST_DB = "OMP_TEST";

    private TestFixtures() {}

    public static void resetAndSeed(JdbcTemplate jdbc) {
        assertTestDatabase(jdbc);
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String t : new String[]{"reviews", "orders", "order_menu", "carts", "shop_review_stats", "shops", "users"}) {
            jdbc.execute("DELETE FROM " + t);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");

        jdbc.update("INSERT INTO users(user_id, email, name, status) VALUES (?, ?, ?, ?)", USER_OK, "ok@test.com", "ok", "GENERAL");
        jdbc.update("INSERT INTO users(user_id, email, name, status) VALUES (?, ?, ?, ?)", USER_BANNED, "ban@test.com", "ban", "BAN");
        jdbc.update("INSERT INTO shops(shop_id, name, category, is_open) VALUES (?, ?, ?, ?)", SHOP_OPEN, "open", "PIZZA", 1);
        jdbc.update("INSERT INTO shops(shop_id, name, category, is_open) VALUES (?, ?, ?, ?)", SHOP_CLOSED, "closed", "PIZZA", 0);
        jdbc.update("INSERT INTO shop_review_stats(shop_id, review_count, rating_sum, average_rating) VALUES (?, 0, 0, 0)", SHOP_OPEN);
        jdbc.update("INSERT INTO shop_review_stats(shop_id, review_count, rating_sum, average_rating) VALUES (?, 0, 0, 0)", SHOP_CLOSED);
        jdbc.update("INSERT INTO carts(cart_id, user_id, shop_id) VALUES (?, ?, ?)", CART_OK, USER_OK, SHOP_OPEN);
    }

    /**
     * 마스터 테이블을 지우기 전에 접속 스키마를 확인한다.
     * 프로필 파일이 빠지면 기본 설정(OMP = 벤치마크 DB)으로 붙어 seed 데이터를 전부 지우게 되므로 여기서 막는다.
     */
    public static void assertTestDatabase(JdbcTemplate jdbc) {
        String schema = jdbc.queryForObject("SELECT DATABASE()", String.class);
        if (schema == null || !schema.equalsIgnoreCase(TEST_DB)) {
            throw new IllegalStateException("integration tests must run against " + TEST_DB
                    + " but connected to '" + schema + "'. Is src/test/resources/application-test.properties present?");
        }
    }

    public static int count(JdbcTemplate jdbc, String table) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }

    /** 조건이 참이 될 때까지 폴링. 시간 초과면 false. */
    public static boolean awaitUntil(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return true;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }
}
