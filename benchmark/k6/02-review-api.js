// 02. 리뷰 생성 API 경합 테스트 (데드락 / 통계 정합성 검증)
// SHOP_POOL을 작게 잡아 "같은 가게"에 동시 리뷰를 집중시킨다.
// ※ 이것은 결함 재현을 위한 경합 조건이지 실제 리뷰 트래픽 모델이 아니다.
//   데드락은 동시 요청 2건이면 발생 가능한 정합성·가용성 결함이므로 빈도가 아닌 결함 관점으로 측정한다.
//
// 비교 3단계 (README 1절). closed는 VUS, open은 RATE를 고정하고 SHOP_POOL·MODEL·DURATION도 비교군에 동일 적용한다.
// ①→②는 통계 모델 분리 + 원자 갱신의 결합 효과, ②→③은 트랜잭션·실행 스레드 분리의 추가 효과다.
// TAG는 결과 식별자일 뿐 서버 모드를 바꾸지 않는다. 회차마다 서버 브랜치·기동 옵션과 초기화를 확인한다.
//   ① shops 통계 + 같은 트랜잭션       : git checkout bench/review-before        → -e TAG=before -e THRESHOLDS=off
//   ② 별도 통계 + 같은 트랜잭션        : main, --omp.review.stats.mode=sync 기동  → -e TAG=sync
//   ③ 별도 통계 + AFTER_COMMIT 비동기  : main, 기본 기동                          → -e TAG=async
//
// 환경변수
//   MODEL      closed    : constant-vus 로 VUS(기본 50)명이 응답 뒤 다음 요청(nGrinder vUser 방식). VU 수가 고정되며 유입률은 응답 시간에 따라 바뀐다.
//                          같은 VU 수에서의 성공 응답 수(reviews_created)·지연·정합성을 비교한다. **리뷰 ①②③ 본측정은 이 모드로 한다.**
//              open(기본): constant-arrival-rate 로 RATE/s 유지. 유입량을 고정한 지연 비교가 필요할 때만.
//   SHOP_POOL  작을수록 경합 심함 (기본 10, 파일럿 출발값 3). 1분 파일럿에서 원인·부하기 여유를 확인한 뒤 조건을 확정하고 세 단계에 동일 적용.
//   THRESHOLDS strict(기본): 실패 0, check 100%, p95<500ms. ②·③용.
//              off        : threshold 없음. ①은 결함 재현 회차이므로 off. lock_deadlocks 증가분(verify.sql 1)과 락 로그를 확인하고 5xx 원인을 대조한다.
//                          실패율이 1% 미만이어도 데드락은 발생한다. ②·③도 HTTP threshold 통과와 최종 통계 정합성은 별도로 확인한다.
//   TAG        before | sync | async (+회차). OUT_DIR 결과 폴더(미리 존재해야 함).
//
// 명령은 저장소 루트 기준. 워밍업에서 closed 모드의 RATE만 낮춰도 VUS는 바뀌지 않으므로 MODEL·VUS를 확인한다.
// 스모크:  k6 run -e BASE_URL=http://<서버IP>:8080 -e MODEL=open -e RATE=5 -e DURATION=30s benchmark/k6/02-review-api.js
// 파일럿:  k6 run -e BASE_URL=http://<서버IP>:8080 -e MODEL=closed -e VUS=50 -e SHOP_POOL=3 -e DURATION=1m -e THRESHOLDS=off -e TAG=before-pilot benchmark/k6/02-review-api.js
// 본측정:  k6 run -e BASE_URL=http://<서버IP>:8080 -e MODEL=closed -e VUS=50 -e SHOP_POOL=3 -e DURATION=15m -e TAG=async-r1 -e OUT_DIR=benchmark/results --out csv=benchmark/results/review_async_r1.csv benchmark/k6/02-review-api.js
//          처리량은 iterations 가 아니라 reviews_created(2xx) 로 비교한다. ①의 빠른 500 응답이 iterations 를 부풀린다.
//          summary 지연은 전체 응답 기준. 성공 응답 p95·p99는 CSV의 http_req_duration 중 2xx 표본을 별도로 집계한다.

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

const created = new Counter('reviews_created');  // 리뷰 저장 성공 응답(2xx). ③의 통계 반영 완료까지 의미하지 않음
const failed5xx = new Counter('reviews_5xx');    // 데드락 외 5xx도 포함. lock_deadlocks 증가분·락 로그와 대조한다
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
//   2. ②·③: verify.sql 2)·2-b)·2-c) 모두 0행인지 확인. ③은 rejected / failed 증가분도 0인지 확인한다.
//      불일치 가게 수와 미반영 갱신 수를 구분한다. 개수 부족/초과분 집계는 verify.sql 주석 참고.
//      한 가게에 여러 갱신이 누락될 수 있으므로 불일치 행 수 = rejected + failed 로 계산하지 않는다.
//   3. ①: lock_deadlocks 증가분·락 로그, verify.sql 4-a) 불일치 가게·개수 차이를 기록하고 5xx 원인을 대조한다.
//      이 회차는 shop_review_stats 를 갱신하지 않으므로 verify.sql 2)·2-b)·2-c) 를 근거로 쓰지 않는다.
//   4. reviews_created와 SELECT COUNT(*) FROM reviews를 함께 기록. ②의 실패는 HTTP·롤백·로그, ③의 통계 실패는 비동기 카운터·로그로 확인한다.
//   5. 잔여 작업 종료는 최종 정합성이나 개별 리뷰 반영 지연을 보장하지 않는다. 실패 건수는 3회 중앙값 대신 각 회차 모두 기록한다.
// ────────────────────────────────────────────────
