package com.omp.shop;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 리뷰 통계 갱신 방식.
 * SYNC  (채택): 리뷰 INSERT와 같은 트랜잭션에서 UPDATE. 리뷰와 통계가 원자적으로 일치한다. 핫 가게의 stats 행 락 대기가 응답에 포함된다.
 * ASYNC (비교군): 리뷰 커밋 후(AFTER_COMMIT) 별도 스레드·트랜잭션에서 UPDATE. 응답에서 락 대기를 빼지만 반영 지연과
 *                 거절·실패 시 복구되지 않는 불일치가 생긴다.
 * 두 모드 모두 기존 shops 행의 S락→X락 승격 경로는 없다(모든 데드락이 불가능하다는 뜻은 아니다).
 * 선택 기준(2026-09-24 확정): 원본 저장 > 통계의 최종 정합성 > 통계 신선도 > 작성 응답 지연.
 * 응답 p95가 허용 한도 안이면 SYNC를 유지한다. 근거와 검증 절차는 benchmark/README.md 2절.
 */
@ConfigurationProperties(prefix = "omp.review.stats")
public record ReviewStatsProperties(@DefaultValue("sync") Mode mode) {

    public enum Mode { SYNC, ASYNC }

    public boolean isSync() {
        return mode == Mode.SYNC;
    }
}
