package com.omp.promotion;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 이벤트 생성·초기화·정합성 확인(벤치마크·테스트용). 생성할 때 세 방식에 필요한 행을 모두 만든다:
 * 재고 행 하나(promotions.remaining_stock), 재고 조각(promotion_stock_buckets), 번호표 1~총 재고(promotion_tickets).
 */
@Service
@RequiredArgsConstructor
public class PromotionAdminService {
    static final int MAX_STOCK = 1_000_000;

    /** 0~999,999 + 1을 만드는 여섯 자리 숫자 조합에서 총 재고까지만 넣는다(재귀 CTE의 깊이 제한을 피한다). */
    private static final String DIGITS = "(SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 "
            + "UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)";
    private static final String INSERT_TICKETS = "INSERT INTO promotion_tickets (promotion_id, ticket_no) SELECT ?, n FROM ("
            + "SELECT d0.d + d1.d * 10 + d2.d * 100 + d3.d * 1000 + d4.d * 10000 + d5.d * 100000 + 1 AS n FROM "
            + DIGITS + " d0 CROSS JOIN " + DIGITS + " d1 CROSS JOIN " + DIGITS + " d2 CROSS JOIN "
            + DIGITS + " d3 CROSS JOIN " + DIGITS + " d4 CROSS JOIN " + DIGITS + " d5) t WHERE n <= ?";

    private final JdbcTemplate jdbc;
    private final PromotionCatalog catalog;
    private final PromotionTicketAllocator allocator;
    private final PromotionProperties props;

    @Transactional
    public long create(String name, int totalStock, LocalDateTime startsAt, LocalDateTime endsAt) {
        if (totalStock < 1 || totalStock > MAX_STOCK) {
            throw new IllegalArgumentException("총 재고는 1~" + MAX_STOCK + " : " + totalStock);
        }
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO promotions (name, total_stock, remaining_stock, starts_at, ends_at) "
                    + "VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setInt(2, totalStock);
            ps.setInt(3, totalStock);
            ps.setTimestamp(4, Timestamp.valueOf(startsAt));
            ps.setTimestamp(5, Timestamp.valueOf(endsAt));
            return ps;
        }, key);
        long id = key.getKey().longValue();

        int buckets = props.buckets();
        List<Object[]> rows = new ArrayList<>();
        for (int b = 0; b < buckets; b++) {
            int stock = totalStock / buckets + (b < totalStock % buckets ? 1 : 0);
            rows.add(new Object[]{id, b, stock, stock});
        }
        jdbc.batchUpdate("INSERT INTO promotion_stock_buckets (promotion_id, bucket_no, initial_stock, remaining) VALUES (?, ?, ?, ?)", rows);
        jdbc.update(INSERT_TICKETS, id, totalStock);
        evictAfterCommit(id);
        return id;
    }

    /** 참여를 지우고 재고를 처음으로 되돌린다. 메모리 번호표는 다음 예약 때 DB에서 다시 읽는다. */
    @Transactional
    public void reset(long promotionId) {
        jdbc.update("DELETE FROM promotion_participations WHERE promotion_id = ?", promotionId);
        jdbc.update("UPDATE promotions SET remaining_stock = total_stock WHERE promotion_id = ?", promotionId);
        jdbc.update("UPDATE promotion_stock_buckets SET remaining = initial_stock WHERE promotion_id = ?", promotionId);
        evictAfterCommit(promotionId);
    }

    /**
     * 정합성 확인용 숫자(설계 4.3). 방식마다 "남은 재고 + 참여 행 수 = 총 재고"가 맞아야 하고, 참여 행 수는 총 재고를 넘을 수 없다.
     * 메모리 방식은 확인 대기(pending) 중인 예약이 있으면 그만큼 남은 재고에서 빠져 보인다.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> check(long promotionId) {
        List<Integer> total = jdbc.queryForList("SELECT total_stock FROM promotions WHERE promotion_id = ?", Integer.class, promotionId);
        if (total.isEmpty()) {
            throw new PromotionRejectedException(PromotionRejectedException.Reason.NOT_FOUND);   // → 404
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("promotionId", promotionId);
        out.put("stockMode", props.stockMode().name());
        out.put("totalStock", total.get(0));
        out.put("participations", jdbc.queryForObject("SELECT COUNT(*) FROM promotion_participations WHERE promotion_id = ?", Integer.class, promotionId));
        out.put("remainingSingle", jdbc.queryForObject("SELECT remaining_stock FROM promotions WHERE promotion_id = ?", Integer.class, promotionId));
        out.put("remainingBuckets", jdbc.queryForObject("SELECT COALESCE(SUM(remaining), 0) FROM promotion_stock_buckets WHERE promotion_id = ?", Integer.class, promotionId));
        out.put("remainingMemory", allocator.remaining(promotionId));
        out.put("pendingUnknown", allocator.pendingCount());
        out.put("distinctTickets", jdbc.queryForObject("SELECT COUNT(DISTINCT ticket_no) FROM promotion_participations WHERE promotion_id = ?", Integer.class, promotionId));
        return out;
    }

    private void evictAfterCommit(long promotionId) {
        catalog.evict(promotionId);
        allocator.reload(promotionId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                catalog.evict(promotionId);
                allocator.reload(promotionId);
            }
        });
    }
}
