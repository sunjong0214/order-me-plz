package com.omp.shop;

import static com.omp.support.TestFixtures.SHOP_CLOSED;
import static org.assertj.core.api.Assertions.assertThat;

import com.omp.shop.dto.CreateShopDto;
import com.omp.shop.dto.ShopInfo;
import com.omp.shop.dto.ShopUpdateRequest;
import com.omp.support.TestFixtures;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 가게 목록 캐시(포폴 1-3): 커서 정렬, 변경 시 무효화, 같은 키의 동시 미스 합치기(sync).
 * HTTP와 같은 캐시 경계(컨트롤러 빈의 @Cacheable 프록시)를 직접 호출한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ShopListCacheTest {
    private static final int PAGE = 20;

    @Autowired ShopController shopController;
    @Autowired ShopService shopService;
    @Autowired CacheManager cacheManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired EntityManagerFactory emf;
    @Autowired MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        TestFixtures.resetAndSeed(jdbc);   // PIZZA: SHOP_OPEN(영업), SHOP_CLOSED(휴업)
        cacheManager.getCache("shops").clear();
    }

    @Test
    void 커서로_이어_조회하면_id_내림차순으로_중복_누락_없이_나온다() {
        List<Long> inserted = insertShops(ShopCategory.CHICKEN, 45);

        List<Long> seen = new ArrayList<>();
        Long cursor = null;
        Slice<ShopInfo> page;
        int pages = 0;
        do {
            page = shopController.getShops(ShopCategory.CHICKEN, cursor, PAGE);
            List<Long> ids = page.getContent().stream().map(ShopInfo::getShopId).toList();
            assertThat(ids).isSortedAccordingTo(Comparator.reverseOrder());
            if (cursor != null) {
                assertThat(ids.get(0)).isLessThan(cursor);
            }
            seen.addAll(ids);
            cursor = ids.get(ids.size() - 1);
            pages++;
        } while (page.hasNext() && pages < 10);

        assertThat(pages).isEqualTo(3);   // 20 + 20 + 5
        assertThat(seen).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(inserted);
    }

    @Test
    void 영업_상태를_바꾸면_캐시가_무효화되어_바로_반영된다() {
        assertThat(isOpenInList(SHOP_CLOSED)).isFalse();   // 첫 조회로 캐시가 채워진다

        jdbc.update("UPDATE shops SET is_open = 1 WHERE shop_id = ?", SHOP_CLOSED);
        assertThat(isOpenInList(SHOP_CLOSED)).isFalse();   // DB만 바꾸면 캐시된 값이 보인다 (캐시가 동작한다는 확인)

        shopService.updateStatus(new ShopUpdateRequest(SHOP_CLOSED, true, null, null));
        assertThat(isOpenInList(SHOP_CLOSED)).isTrue();    // 서비스로 바꾸면 커밋 후 무효화되어 바로 보인다
    }

    @Test
    void 가게를_추가하면_목록_캐시가_무효화된다() {
        int before = shopController.getShops(ShopCategory.PIZZA, null, PAGE).getContent().size();

        Long newId = shopService.saveShopBy(new CreateShopDto("new pizza", ShopCategory.PIZZA));

        Slice<ShopInfo> after = shopController.getShops(ShopCategory.PIZZA, null, PAGE);
        assertThat(after.getContent()).hasSize(before + 1);
        assertThat(after.getContent().get(0).getShopId()).isEqualTo(newId);   // id 내림차순의 첫 번째
    }

    @Test
    void 같은_키의_동시_미스는_목록_쿼리를_한_번만_실행한다() throws Exception {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        int callers = 10;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        List<Future<Slice<ShopInfo>>> results = new ArrayList<>();

        try (Connection locker = dataSource.getConnection(); Statement lock = locker.createStatement()) {
            // 목록 쿼리를 잠시 막아, 요청들이 모두 캐시 미스 상태로 겹치게 만든다
            lock.execute("LOCK TABLES shops WRITE, shop_review_stats WRITE");
            try {
                stats.clear();
                for (int i = 0; i < callers; i++) {
                    results.add(pool.submit(() -> shopController.getShops(ShopCategory.PIZZA, null, PAGE)));
                }
                Thread.sleep(500);   // 모든 요청이 캐시에 도달할 시간
            } finally {
                lock.execute("UNLOCK TABLES");   // 커넥션을 풀에 돌려주기 전에 반드시 해제
            }
            for (Future<Slice<ShopInfo>> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS).getContent()).hasSize(2);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(stats.getQueryExecutionCount()).isEqualTo(1);
    }

    @Test
    void 적중과_미스가_cache_gets_지표로_기록된다() {
        double hitsBefore = cacheGets("hit");
        double missesBefore = cacheGets("miss");

        shopController.getShops(ShopCategory.PIZZA, null, PAGE);   // 미스 → DB 조회 후 저장
        shopController.getShops(ShopCategory.PIZZA, null, PAGE);   // 적중

        assertThat(cacheGets("miss") - missesBefore).isEqualTo(1.0);
        assertThat(cacheGets("hit") - hitsBefore).isEqualTo(1.0);
    }

    private double cacheGets(String result) {
        var counter = meterRegistry.find("cache.gets").tag("cache", "shops").tag("result", result).functionCounter();
        return counter == null ? 0.0 : counter.count();
    }

    private boolean isOpenInList(long shopId) {
        return shopController.getShops(ShopCategory.PIZZA, null, PAGE).getContent().stream()
                .filter(s -> s.getShopId() == shopId)
                .findFirst().orElseThrow()
                .isOpen();
    }

    private List<Long> insertShops(ShopCategory category, int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update("INSERT INTO shops(name, category, is_open) VALUES (?, ?, 1)", "shop" + i, category.name());
        }
        jdbc.update("INSERT INTO shop_review_stats(shop_id, review_count, rating_sum, average_rating) "
                + "SELECT shop_id, 0, 0, 0 FROM shops WHERE category = ?", category.name());
        return jdbc.queryForList("SELECT shop_id FROM shops WHERE category = ?", Long.class, category.name());
    }
}
