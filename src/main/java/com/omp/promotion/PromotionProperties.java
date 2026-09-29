package com.omp.promotion;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 선착순 한정 수량 할인 설정 (설계: benchmark/DESIGN-promotion-stock.md).
 *
 * stockMode — 재고 처리 방식.
 *   DB_SINGLE: 재고 행 하나를 주문 트랜잭션 마지막에 조건부 UPDATE. 보상 없음, 할인 주문 커밋이 하나씩 된다.
 *   DB_BUCKET: 재고를 buckets개 행으로 나눠 조건부 UPDATE. 보상 없음, 경합이 약 1/buckets.
 *   MEMORY   : 메모리 번호표 발급기가 트랜잭션 밖에서 판정하고, 번호 중복·범위 초과는 DB 제약이 막는다. 보상 규칙이 있다.
 * buckets — DB_BUCKET의 행 수. 비동기 워커 수와 같게 두면 워커마다 자기 행을 맡는다.
 * reconcileInterval — 커밋 결과를 모르는 예약을 DB에서 확인하는 주기.
 * adminApi — 이벤트 생성·초기화 API(벤치마크·테스트용)를 켤지. 기본 꺼짐.
 */
@ConfigurationProperties(prefix = "omp.promotion")
public record PromotionProperties(@DefaultValue("memory") StockMode stockMode,
                                  @DefaultValue("20") int buckets,
                                  @DefaultValue("1s") Duration reconcileInterval,
                                  @DefaultValue("false") boolean adminApi) {

    public enum StockMode { DB_SINGLE, DB_BUCKET, MEMORY }

    public PromotionProperties {
        if (buckets < 1) {
            throw new IllegalArgumentException("omp.promotion.buckets 는 1 이상 : " + buckets);
        }
    }
}
