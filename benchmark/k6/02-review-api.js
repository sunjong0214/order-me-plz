// 02. 리뷰 생성 API 경합 테스트 (데드락 / 통계 정합성 검증)
// SHOP_POOL을 작게 잡아 "같은 가게"에 동시 리뷰를 집중시킨다.
// ※ 이것은 결함 재현을 위한 경합 조건이지 실제 리뷰 트래픽 모델이 아니다.
//   데드락은 동시 요청 2건이면 발생 가능한 정합성·가용성 결함이므로 빈도가 아닌 결함 관점으로 측정한다.
//
// 개선 전: git checkout bench/review-before
//   → 데드락으로 500이 섞이는 게 정상. http_req_failed threshold가 깨지는 것 자체가 증거.
//   → 회차 전 shops의 구 설계 컬럼 초기화 필요 (README의 "리뷰 개선 전 회차" 참고)
// 개선 후: git checkout main
//   → 실패 0 + 종료 후 sql/verify.sql 0행이 증거.
//
// 데드락 건수 확인: SELECT count FROM information_schema.INNODB_METRICS WHERE name='lock_deadlocks';
// (테스트 전/후 값의 차이 = 테스트 중 발생 건수)
//
// 스모크:  k6 run -e BASE_URL=http://<서버IP>:8080 -e RATE=5 -e DURATION=30s 02-review-api.js
// 본측정:  k6 run -e BASE_URL=http://<서버IP>:8080 -e RATE=20 -e DURATION=15m -e SHOP_POOL=10 02-review-api.js
//   RATE는 전/후 모두 감당 가능한 수준으로 고정해 지연·에러율을 비교한다.
//   경합 강도를 더 높이려면 SHOP_POOL=1.

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = Number(__ENV.RATE || 20);
const DURATION = __ENV.DURATION || '15m';
const SHOP_POOL = Number(__ENV.SHOP_POOL || 10); // 작을수록 경합 심함
const WRITER_POOL = Number(__ENV.WRITER_POOL || 100000);

const created = new Counter('reviews_created');

export const options = {
  scenarios: {
    reviews: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 100,
      maxVUs: 1000,
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
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
}

export function handleSummary(data) {
  const ts = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [`review_${ts}.json`]: JSON.stringify(data, null, 2),
  };
}

// ────────────────────────────────────────────────
// 테스트 종료 후 검증 (이번 측정의 핵심 증거):
//   1. 비동기 갱신이 끝날 시간을 주기 위해 종료 1~2분 뒤 sql/verify.sql 실행
//      → 정합성 쿼리 0행 = "동시 부하에서 통계 정합성 유지" 증명
//   2. 애플리케이션 로그에서 "review stats update fail" 건수 확인 (0이어야 함)
//   3. lock_deadlocks 전/후 차이 기록
// ────────────────────────────────────────────────
