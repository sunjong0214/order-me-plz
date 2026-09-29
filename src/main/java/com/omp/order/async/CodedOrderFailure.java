package com.omp.order.async;

/**
 * 워커 단계의 실패 중 사용자에게 이유를 알려 줄 수 있는 것(예: 할인 매진). 상태 조회의 failureCode로 나가고,
 * 정상 흐름의 결과이므로 건별 경고 로그를 남기지 않는다(초당 수천 건 나올 수 있다).
 */
public interface CodedOrderFailure {
    String failureCode();
}
