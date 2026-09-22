// 02. 리뷰 생성 API 경합 테스트 (데드락 / 통계 정합성 검증)
// SHOP_POOL을 작게 잡아 "같은 가게"에 동시 리뷰를 집중시킨다.
// ※ 이것은 결함 재현을 위한 경합 조건이지 실제 리뷰 트래픽 모델이 아니다.
//   데드락은 동시 요청 2건이면 발생 가능한 정합성·가용성 결함이므로 빈도가 아닌 결함 관점으로 측정한다.
//
// 비교 3단계 (README 1절). RATE·SHOP_POOL·MODEL 은 세 단계 모두 동일해야 한다.
//   ① shops 통계 + 같은 트랜잭션       : git checkout bench/review-before        → -e TAG=before -e THRESHOLDS=off
//   ② 별도 통계 + 같은 트랜잭션        : main, --omp.review.stats.mode=sync 기동  → -e TAG=sync
//   ③ 별도 통계 + AFTER_COMMIT 비동기  : main, 기본 기동                          → -e TAG=async
//
// 환경변수
//   MODEL      open(기본): constant-arrival-rate 로 RATE/s 유지. 전·후 지연·에러율 비교용.
//              closed    : constant-vus 로 VUS(기본 50)명이 쉬지 않고 요청. 요청이 항상 겹치므로
//                          ①에서 데드락이 나는 조건을 찾는 1분 파일럿용. 찾은 조건을 ①~③에 동일 적용한다.
//   SHOP_POOL  작을수록 경합 심함 (기본 10). RATE=20·SHOP_POOL=10 open model 은 요청이 잘 겹치지 않아
//              ①에서도 데드락이 거의 안 날 수 있다 → 파일럿으로 먼저 확정할 것.
//   THRESHOLDS strict(기본): 실패 0, check 100%, p95<500ms. ②·③용.
//              off        : threshold 없음. ①은 500이 나는 게 정상이므로 off. 판정은 threshold가 아니라
//                          lock_deadlocks 증가분(verify.sql 1)과 reviews_5xx 건수로 한다. 실패율이 1% 미만이어도 데드락은 발생한다.
//   TAG        before | sync | async (+회차). OUT_DIR 결과 폴더(미리 존재해야 함).
//
// 스모크:  k6 run -e BASE_URL=http://<서버IP>:8080 -e RATE=5 -e DURATION=30s 02-review-api.js
// 파일럿:  k6 run -e BASE_URL=http://<서버IP>:8080 -e MODEL=closed -e VUS=50 -e SHOP_POOL=3 -e DURATION=1m -e THRESHOLDS=off -e TAG=before-pilot 02-review-api.js
// 본측정:  k6 run -e BASE_URL=http://<서버IP>:8080 -e RATE=20 -e DURATION=15m -e SHOP_POOL=10 -e TAG=async -e OUT_DIR=benchmark/results 02-review-api.js

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const MODEL = (__ENV.MODEL || 'open').toLowerCase();             // open | closed
const THRESHOLDS = (__ENV.THRESHOLDS || 'strict').toLowerCase(); // strict | off
const RATE = Number(__ENV.RATE || 20);
const VUS = Number(__ENV.VUS || 50);
const DURATION = __ENV.DURATION || '15m';
const SHOP_POOL = Number(__ENV.SHOP_POOL || 10);
const WRITER_POOL = Number(__ENV.WRITER_POOL || 100000); // reviews.writer_id 는 FK가 아니다
const TAG = __ENV.TAG || MODEL;
const OUT_DIR = (__ENV.OUT_DIR || '.').replace(/[\\/]+$/, '');

const created = new Counter('reviews_created');  // 2xx. "접수량"은 이 값이다 (iterations 아님)
const failed5xx = new Counter('reviews_5xx');    // ①에서는 데드락 롤백(500). lock_deadlocks 증가분과 대조한다
const failedOther = new Counter('reviews_failed_other');

function scenario() {
  if (MODEL === 'closed') {
    return { executor: 'constant-vus', vus: VUS, duration: DURATION };
  }
  return {
    executor: 'constant-arrival-rate',
    rate: RATE,
    timeUnit: '1s',
    duration: DURATION,
    preAllocatedVUs: 100,
    maxVUs: Number(__ENV.MAX_VUS || 1000),
  };
}

const thresholds = THRESHOLDS === 'off' ? {} : {
  http_req_failed: ['rate==0'],
  checks: ['rate==1'],
  http_req_duration: ['p(95)<500'],
};

export const options = {
  scenarios: { reviews: scenario() },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds,
};

export default function () {
  const shopId = Math.floor(Math.random() * SHOP_POOL) + 1;
  const rating = Math.floor(Math.random() * 5) + 1;

  const payload = JSON.stringify({
    title: 'k6 load test',
    writerId: Math.floor(Math.random() * WRITER_POOL) + 1,
    shopId: shopId,
    detail: 'k6 load test review',
    rating: rating,
  });

  const res = http.post(`${BASE}/api/v1/reviews`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'POST /api/v1/reviews' },
  });

  const ok = check(res, {
    'status is 2xx': (r) => r.status >= 200 && r.status < 300,
  });

  if (ok) created.add(1);
  else if (res.status >= 500) failed5xx.add(1);
  else failedOther.add(1);
}

export function handleSummary(data) {
  const ts = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [`${OUT_DIR}/review_${TAG}_${ts}.json`]: JSON.stringify(data, null, 2),
  };
}

// ────────────────────────────────────────────────
// 테스트 종료 후 검증:
//   1. 완료 대기: reviewStatsExecutor 의 executor.queued=0·active=0 (③만 해당) 확인 후 sql/verify.sql 실행.
//      "1~2분 뒤"가 아니라 조건 충족까지 기다리고, 제한 시간(5분) 초과면 미완료로 기록한다.
//   2. ②·③: verify.sql 2)·2-b)·2-c) 0행 + omp.review.stats.rejected / failed 증가분 0 = "동시 부하에서 통계 정합성 유지".
//      거절·실패가 있으면 그 수만큼 2)에 불일치가 남아야 한다(재처리 없음 → 불일치 지속).
//   3. ①: reviews_5xx ≈ lock_deadlocks 증가분, verify.sql 4-a) 유실 행, SHOW ENGINE INNODB STATUS 의 LATEST DETECTED DEADLOCK 캡처.
//      이 회차에서 verify.sql 2)·2-b)·2-c) 는 shop_review_stats 를 갱신하지 않으므로 전부 불일치로 나온다. 근거로 쓰지 않는다.
//   4. 애플리케이션 로그 "review stats update fail" 건수 (②·③에서 0이어야 함).
// ────────────────────────────────────────────────
