package com.omp.promotion;

import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** DB_BUCKET 방식의 재고 조각. 재고를 여러 행으로 나눠 할인 주문끼리 같은 행의 잠금을 덜 기다리게 한다. 스키마 정의용. */
@Entity
@Table(name = "promotion_stock_buckets")
@IdClass(PromotionStockBucket.Key.class)
@NoArgsConstructor(access = PROTECTED)
public class PromotionStockBucket {
    @Id
    @Column(name = "promotion_id")
    private Long promotionId;

    @Id
    @Column(name = "bucket_no")
    private Integer bucketNo;

    @Column(name = "initial_stock", nullable = false)
    private int initialStock;

    @Column(nullable = false)
    private int remaining;

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long promotionId;
        private Integer bucketNo;
    }
}
