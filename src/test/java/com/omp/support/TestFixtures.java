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

    private TestFixtures() {}

    public static void resetAndSeed(JdbcTemplate jdbc) {
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
