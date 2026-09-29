package com.omp.promotion;

import com.omp.promotion.PromotionRejectedException.Reason;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 이벤트 정의(총 재고·기간) 캐시. 이벤트 중에는 바뀌지 않으므로 처음 한 번만 DB에서 읽고,
 * 시간 밖 요청은 DB 없이 거절한다. 이벤트를 만들거나 초기화하면 evict로 비운다.
 */
@Component
@RequiredArgsConstructor
public class PromotionCatalog {
    public record Info(long id, int totalStock, LocalDateTime startsAt, LocalDateTime endsAt) {}

    private final JdbcTemplate jdbc;
    private final PromotionMetrics metrics;
    private final Map<Long, Optional<Info>> cache = new ConcurrentHashMap<>();

    /** 진행 중인 이벤트를 돌려준다. 없으면 NOT_FOUND, 기간 밖이면 NOT_OPEN. */
    public Info requireOpen(long promotionId) {
        Info info = cache.computeIfAbsent(promotionId, this::load)
                .orElseThrow(() -> metrics.rejected(Reason.NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(info.startsAt()) || !now.isBefore(info.endsAt())) {
            throw metrics.rejected(Reason.NOT_OPEN);
        }
        return info;
    }

    public void evict(long promotionId) {
        cache.remove(promotionId);
    }

    private Optional<Info> load(long promotionId) {
        List<Info> rows = jdbc.query(
                "SELECT promotion_id, total_stock, starts_at, ends_at FROM promotions WHERE promotion_id = ?",
                (rs, i) -> new Info(rs.getLong(1), rs.getInt(2),
                        rs.getTimestamp(3).toLocalDateTime(), rs.getTimestamp(4).toLocalDateTime()),
                promotionId);
        return rows.stream().findFirst();
    }
}
