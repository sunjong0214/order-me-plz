package com.omp.promotion;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.NoArgsConstructor;

/**
 * 선착순 할인 이벤트. 스키마 정의용 엔티티이고, 주문 경로는 JdbcTemplate으로 읽고 쓴다(조건부 UPDATE의 바뀐 행 수가 필요하다).
 * remainingStock은 DB_SINGLE 방식에서만 쓴다.
 */
@Entity
@Table(name = "promotions")
@NoArgsConstructor(access = PROTECTED)
public class Promotion {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "promotion_id")
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "total_stock", nullable = false)
    private int totalStock;

    @Column(name = "remaining_stock", nullable = false)
    private int remainingStock;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;
}
