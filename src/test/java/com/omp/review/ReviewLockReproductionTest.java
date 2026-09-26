package com.omp.review;

import static com.omp.support.TestFixtures.SHOP_OPEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.omp.support.TestFixtures;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 리뷰 결함의 결정적 재현 (포폴 1-1 "부하 전 원인 재현").
 * 두 트랜잭션(커넥션 2개)의 실행 순서를 한 단계씩 통제하고, 같은 순서를 두 설계에 적용한다.
 * <ul>
 *   <li>① shops 통계 + 같은 트랜잭션 + 읽고-계산-쓰기: bench/review-before 의 ReviewService 가 실행하는 SQL 순서
 *       (가게 조회 → 리뷰 INSERT → shops 통계를 계산한 절대값으로 UPDATE)</li>
 *   <li>② shop_review_stats 분리 + 같은 트랜잭션 + 원자 UPDATE: main 의 sync 모드가 실행하는 SQL 순서
 *       (리뷰 INSERT → ShopReviewStatsRepository.applyReviewRating 과 같은 증가 UPDATE)</li>
 * </ul>
 * (a) 데드락: 두 트랜잭션이 모두 리뷰 INSERT(외래 키 검사로 shops 행에 S락)를 마친 뒤 통계를 UPDATE 한다.
 * (b) 갱신 유실: 두 트랜잭션이 같은 이전 통계를 읽은 뒤 차례로 커밋한다(포폴 예시: 리뷰 10개·합계 30에 5점과 4점).
 * 데드락 판정은 본측정과 같이 information_schema.INNODB_METRICS 의 lock_deadlocks 증가분으로 한다.
 * 락 대기는 시간에 기대지 않고 performance_schema.data_lock_waits 에 대기가 보일 때까지 확인한 뒤 다음 단계로 간다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReviewLockReproductionTest {
    private static final int ER_LOCK_DEADLOCK = 1213;
    private static final long SHOP = SHOP_OPEN;

    @Autowired JdbcTemplate jdbc;
    @Value("${spring.datasource.url}") String url;
    @Value("${spring.datasource.username}") String username;
    @Value("${spring.datasource.password}") String password;

    private final ExecutorService async = Executors.newSingleThreadExecutor();

    @BeforeEach
    void seed() {
        TestFixtures.resetAndSeed(jdbc);
    }

    @AfterEach
    void tearDown() {
        async.shutdownNow();
        dropDesign1Columns();
    }

    // ── (a) 데드락 ────────────────────────────────────────────────

    @Test
    void 설계1_두_트랜잭션이_FK_S락을_쥔_채_shops를_갱신하면_데드락이_난다() throws Exception {
        addDesign1Columns();
        long deadlocksBefore = deadlocks();

        try (Tx t1 = new Tx(); Tx t2 = new Tx()) {
            Stats r1 = t1.readShops();
            t1.insertReview(5);                                       // T1: shops 행 S락
            Stats r2 = t2.readShops();
            t2.insertReview(4);                                       // T2: 같은 행 S락 (S끼리는 호환)
            Future<?> t1Update = async.submit(() -> t1.writeShops(r1, 5));   // T1: X락 필요 → T2의 S락 대기
            awaitLockWait(t1Update);
            SQLException t2Error = captureSql(() -> t2.writeShops(r2, 4));   // T2: X락 필요 → T1의 S락 대기 → 순환
            SQLException t1Error = sqlErrorOf(t1Update);

            assertThat(t1Error == null ^ t2Error == null).as("정확히 한쪽만 롤백된다").isTrue();
            SQLException victim = t1Error != null ? t1Error : t2Error;
            assertThat(victim.getErrorCode()).isEqualTo(ER_LOCK_DEADLOCK);
            (t1Error == null ? t1 : t2).commit();
        }

        assertThat(deadlocks() - deadlocksBefore).isEqualTo(1);
        assertThat(actualReviewCount()).isEqualTo(1);                 // 데드락 희생자는 리뷰까지 롤백된다
        assertThat(shopsStats().count()).isEqualTo(1);
    }

    @Test
    void 설계2_같은_순서에서는_stats_행_락을_기다린_뒤_둘_다_커밋한다() throws Exception {
        long deadlocksBefore = deadlocks();

        try (Tx t1 = new Tx(); Tx t2 = new Tx()) {
            t1.insertReview(5);                                       // T1: shops 행 S락
            t2.insertReview(4);                                       // T2: shops 행 S락
            t1.applyStats(5);                                         // T1: stats 행 X락. shops 행과 달라 기다리지 않는다
            Future<?> t2Update = async.submit(() -> t2.applyStats(4)); // T2: T1의 stats 행 X락 대기 (순환 없음)
            awaitLockWait(t2Update);
            t1.commit();
            assertThat((Throwable) sqlErrorOf(t2Update)).isNull();    // T1 커밋 후 T2가 이어서 갱신
            t2.commit();
        }

        assertThat(deadlocks() - deadlocksBefore).isZero();
        assertThat(actualReviewCount()).isEqualTo(2);
        Stats stats = statsTable();
        assertThat(stats.count()).isEqualTo(2);
        assertThat(stats.sum()).isEqualByComparingTo("9");
        assertThat(stats.average()).isEqualByComparingTo("4.50");
    }

    // ── (b) 갱신 유실 ─────────────────────────────────────────────

    @Test
    void 설계1_같은_이전_값을_읽고_차례로_커밋하면_앞선_갱신이_덮어써진다() throws Exception {
        addDesign1Columns();
        seedTenReviewsSummingThirty();
        long deadlocksBefore = deadlocks();

        try (Tx t1 = new Tx(); Tx t2 = new Tx()) {
            Stats r1 = t1.readShops();                                // 둘 다 10개·30점을 읽는다
            Stats r2 = t2.readShops();
            t1.insertReview(5);
            t1.writeShops(r1, 5);                                     // 11개·35점
            t1.commit();
            t2.insertReview(4);
            t2.writeShops(r2, 4);                                     // 이전 값 기준 계산: 11개·34점 (T1 결과를 덮어씀)
            t2.commit();
        }

        assertThat(deadlocks() - deadlocksBefore).isZero();           // 데드락이 없는 겹침에서도 생긴다
        assertThat(actualReviewCount()).isEqualTo(12);
        assertThat(actualRatingSum()).isEqualByComparingTo("39");
        Stats shops = shopsStats();
        assertThat(shops.count()).isEqualTo(11);                      // 12가 아니다: 갱신 1건 유실
        assertThat(shops.sum()).isEqualByComparingTo("34");           // 39가 아니다
    }

    @Test
    void 설계2_같은_순서에서도_원자_UPDATE는_유실_없이_합산한다() throws Exception {
        seedTenReviewsSummingThirty();

        try (Tx t1 = new Tx(); Tx t2 = new Tx()) {
            t1.readStatsTable();                                      // 이전 값을 읽어도 쓰기에 쓰지 않는다
            t2.readStatsTable();
            t1.insertReview(5);
            t1.applyStats(5);
            t1.commit();
            t2.insertReview(4);
            t2.applyStats(4);                                         // DB의 현재 값(11개·35점) 기준으로 증가
            t2.commit();
        }

        Stats stats = statsTable();
        assertThat(stats.count()).isEqualTo(12);
        assertThat(stats.sum()).isEqualByComparingTo("39");
        assertThat(stats.average()).isEqualByComparingTo("3.25");
    }

    // ── 스키마·데이터 ──────────────────────────────────────────────

    /** ① 스키마: bench/review-before 의 Shop 엔티티처럼 shops 행에 통계 컬럼을 둔다 (이 테스트 동안만). */
    private void addDesign1Columns() {
        dropDesign1Columns();
        jdbc.execute("ALTER TABLE shops "
                + "ADD COLUMN review_count BIGINT NOT NULL DEFAULT 0, "
                + "ADD COLUMN rating_sum DECIMAL(38,2) NOT NULL DEFAULT 0, "
                + "ADD COLUMN average_rating DECIMAL(38,2) NOT NULL DEFAULT 0");
    }

    private void dropDesign1Columns() {
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shops' AND COLUMN_NAME = 'review_count'", Integer.class);
        if (exists != null && exists > 0) {
            jdbc.execute("ALTER TABLE shops DROP COLUMN review_count, DROP COLUMN rating_sum, DROP COLUMN average_rating");
        }
    }

    /** 포폴 예시의 출발점: 리뷰 10개·평점 합계 30점, 통계도 같은 값. */
    private void seedTenReviewsSummingThirty() {
        for (int i = 0; i < 10; i++) {
            jdbc.update("INSERT INTO reviews(title, writer_id, detail, shop_id, rating) VALUES ('seed', 1, 'd', ?, 3)", SHOP);
        }
        jdbc.update("UPDATE shop_review_stats SET review_count = 10, rating_sum = 30, average_rating = 3.00 WHERE shop_id = ?", SHOP);
        if (hasDesign1Columns()) {
            jdbc.update("UPDATE shops SET review_count = 10, rating_sum = 30, average_rating = 3.00 WHERE shop_id = ?", SHOP);
        }
    }

    private boolean hasDesign1Columns() {
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shops' AND COLUMN_NAME = 'review_count'", Integer.class);
        return exists != null && exists > 0;
    }

    private long deadlocks() {
        Long count = jdbc.queryForObject(
                "SELECT count FROM information_schema.INNODB_METRICS WHERE name = 'lock_deadlocks'", Long.class);
        return count == null ? 0 : count;
    }

    /** blocked 작업이 락 대기 상태가 될 때까지 기다린다. 대기 없이 끝났거나 실패했으면 그 사실을 메시지로 드러낸다. */
    private void awaitLockWait(Future<?> blocked) {
        boolean waiting = TestFixtures.awaitUntil(() -> {
            // INNODB_TRX 는 0.1초 넘게 읽히지 않아야 새로 채워지는 캐시라, 자주 폴링하면 대기 전 상태(RUNNING)가 계속 보인다.
            // performance_schema.data_lock_waits 는 실시간이다.
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class);
            return (n != null && n > 0) || blocked.isDone();
        }, Duration.ofSeconds(5));
        assertThat(blocked.isDone())
                .as("대기해야 할 작업이 먼저 끝났다: %s", describe(blocked))
                .isFalse();
        assertThat(waiting).as("한 트랜잭션이 락을 기다리는 상태가 되어야 한다").isTrue();
    }

    private static String describe(Future<?> future) {
        if (!future.isDone()) {
            return "진행 중";
        }
        try {
            future.get();
            return "정상 완료(락을 기다리지 않음)";
        } catch (ExecutionException e) {
            return "실패: " + e.getCause();
        } catch (Exception e) {
            return e.toString();
        }
    }

    private long actualReviewCount() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE shop_id = ?", Long.class, SHOP);
        return n == null ? 0 : n;
    }

    private BigDecimal actualRatingSum() {
        return jdbc.queryForObject("SELECT COALESCE(SUM(rating), 0) FROM reviews WHERE shop_id = ?", BigDecimal.class, SHOP);
    }

    private Stats shopsStats() {
        return jdbc.queryForObject("SELECT review_count, rating_sum, average_rating FROM shops WHERE shop_id = ?",
                (rs, i) -> new Stats(rs.getLong(1), rs.getBigDecimal(2), rs.getBigDecimal(3)), SHOP);
    }

    private Stats statsTable() {
        return jdbc.queryForObject("SELECT review_count, rating_sum, average_rating FROM shop_review_stats WHERE shop_id = ?",
                (rs, i) -> new Stats(rs.getLong(1), rs.getBigDecimal(2), rs.getBigDecimal(3)), SHOP);
    }

    private static SQLException captureSql(SqlAction action) {
        try {
            action.run();
            return null;
        } catch (SQLException e) {
            return e;
        }
    }

    private static SQLException sqlErrorOf(Future<?> future) throws InterruptedException {
        try {
            future.get(10, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof SQLException sql) {
                return sql;
            }
            throw new AssertionError("예상하지 못한 실패", e.getCause());
        } catch (TimeoutException e) {
            throw new AssertionError("락 대기가 풀리지 않았다", e);
        }
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }

    private record Stats(long count, BigDecimal sum, BigDecimal average) {}

    /** 수동 커밋 트랜잭션 하나. 각 메서드가 설계별 SQL 한 단계다. */
    private final class Tx implements AutoCloseable {
        private final Connection con;

        Tx() throws SQLException {
            con = DriverManager.getConnection(url, username, password);
            con.setAutoCommit(false);
            try (Statement st = con.createStatement()) {
                st.execute("SET SESSION innodb_lock_wait_timeout = 5");   // 재현이 틀어져도 테스트가 오래 멈추지 않게
            }
        }

        void insertReview(int rating) throws SQLException {
            update("INSERT INTO reviews(title, writer_id, detail, shop_id, rating) VALUES ('t', 1, 'd', ?, ?)", SHOP, rating);
        }

        /** ①: 가게와 통계를 읽는다(일관된 읽기, 락 없음). */
        Stats readShops() throws SQLException {
            return read("SELECT review_count, rating_sum, average_rating FROM shops WHERE shop_id = ?");
        }

        /** ①: 읽어 둔 값으로 애플리케이션에서 계산한 절대값을 쓴다(Shop.applyReviewRating + 더티 체킹과 같은 결과). */
        Void writeShops(Stats read, int rating) throws SQLException {
            long count = read.count() + 1;
            BigDecimal sum = read.sum().add(BigDecimal.valueOf(rating));
            BigDecimal average = sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            update("UPDATE shops SET review_count = ?, rating_sum = ?, average_rating = ? WHERE shop_id = ?",
                    count, sum, average, SHOP);
            return null;
        }

        Stats readStatsTable() throws SQLException {
            return read("SELECT review_count, rating_sum, average_rating FROM shop_review_stats WHERE shop_id = ?");
        }

        /** ②: ShopReviewStatsRepository.applyReviewRating 과 같은 원자 증가 UPDATE (평균을 먼저 계산). */
        Void applyStats(int rating) throws SQLException {
            update("UPDATE shop_review_stats SET average_rating = (rating_sum + ?) / (review_count + 1), "
                    + "rating_sum = rating_sum + ?, review_count = review_count + 1 WHERE shop_id = ?", rating, rating, SHOP);
            return null;
        }

        void commit() throws SQLException {
            con.commit();
        }

        private Stats read(String sql) throws SQLException {
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setLong(1, SHOP);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return new Stats(rs.getLong(1), rs.getBigDecimal(2), rs.getBigDecimal(3));
                }
            }
        }

        private void update(String sql, Object... params) throws SQLException {
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                ps.executeUpdate();
            }
        }

        @Override
        public void close() throws SQLException {
            try {
                con.rollback();   // 커밋하지 않은 작업은 버린다 (커밋 후라면 영향 없음)
            } finally {
                con.close();
            }
        }
    }
}
