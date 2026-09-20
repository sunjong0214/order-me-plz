package com.omp.shop;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 리뷰 통계 갱신 방식.
 * SYNC  : 리뷰 INSERT와 같은 트랜잭션에서 UPDATE. 원자적이지만 핫 가게의 stats 행 락 대기가 리뷰 응답 시간에 포함된다.
 * ASYNC : 리뷰 커밋 후(AFTER_COMMIT) 별도 스레드·트랜잭션에서 UPDATE. 응답에서 락 대기를 격리하지만 최종 정합성 창과 유실 경로가 생긴다.
 * 통계 테이블이 분리된 현재 설계에서는 어느 쪽이든 데드락은 발생하지 않으므로, 선택 기준은 지연 격리 vs 원자성이다. 측정으로 결정한다.
 */
@ConfigurationProperties(prefix = "omp.review.stats")
public record ReviewStatsProperties(@DefaultValue("async") Mode mode) {

    public enum Mode { SYNC, ASYNC }

    public boolean isSync() {
        return mode == Mode.SYNC;
    }
}
