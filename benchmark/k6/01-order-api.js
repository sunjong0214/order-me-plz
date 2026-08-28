// 01. 주문 생성 API 부하 테스트 (open model)
// 서버 상태와 무관하게 초당 RATE건을 계속 발사한다.
// → "초당 N건 유입 시 p95/p99가 얼마인가"에 답하는 측정.
//
// MODE=async (기본): POST /api/v1/order/async — 202 + Location 헤더 검증 (개선 후 구조)
// MODE=sync        : POST /api/v1/order       — 200 + orderId 검증 (개선 전 구조)
// 두 엔드포인트가 main에 공존하므로 같은 빌드·같은 스키마에서 전/후를 비교한다.
//
// 스모크:  k6 run -e BASE_URL=http://<서버IP>:8080 -e RATE=10 -e DURATION=30s 01-order-api.js
// 본측정:  k6 run -e BASE_URL=http://<서버IP>:8080 -e RATE=1000 -e DURATION=15m 01-order-api.js
// 동기측정: 위 명령에 -e MODE=sync 추가 (동기 회차는 p95 threshold가 깨지는 것 자체가 비교 증거)
//
// 전제(sql/seed.sql): users 1..USER_POOL, carts 1..USER_POOL(cart N = user N),
//                     shops 1..SHOP_POOL(전부 is_open=1)

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const MODE = (__ENV.MODE || 'async').toLowerCase(); // async | sync
const RATE = Number(__ENV.RATE || 1000);
const DURATION = __ENV.DURATION || '15m';
const USER_POOL = Number(__ENV.USER_POOL || 100000);
const SHOP_POOL = Number(__ENV.SHOP_POOL || 1000);

const PATH = MODE === 'sync' ? '/api/v1/order' : '/api/v1/order/async';
const accepted = new Counter('orders_accepted');

export const options = {
  scenarios: {
    orders: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 300,
      maxVUs: 2000, // dropped_iterations가 뜨면 이 값을 먼저 늘려볼 것
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: {
    // 목표를 먼저 선언한다 — 이 선언 자체가 benchmark 문서의 증거가 된다
    http_req_duration: ['p(95)<200', 'p(99)<500'],
    http_req_failed: ['rate<0.01'],
  },

  // [용량 탐색] "처리량 개선" 주장에는 고정 rate보다 이 방식이 맞다.
  // 감당 가능한 최대 rate를 찾을 때 위 scenarios를 아래로 교체:
  // scenarios: {
  //   capacity: {
  //     executor: 'ramping-arrival-rate',
  //     startRate: 200, timeUnit: '1s',
  //     preAllocatedVUs: 300, maxVUs: 3000,
  //     stages: [
  //       { target: 500,  duration: '3m' },
  //       { target: 1000, duration: '3m' },
  //       { target: 1500, duration: '3m' },
  //       { target: 2000, duration: '3m' },
  //     ],
  //   },
  // },
  // → p95가 목표(200ms)를 뚫는 시점의 rate = 이 환경에서 이 구조의 용량.
  //   sync/async 각각의 용량을 찾아 비교한다.
};

export default function () {
  const userId = Math.floor(Math.random() * USER_POOL) + 1;
  const payload = JSON.stringify({
    ordererId: userId,
    cartId: userId, // seed.sql이 cart N = user N 으로 적재
    shopId: Math.floor(Math.random() * SHOP_POOL) + 1,
    orderMenus: [
      { menuId: 1, cartId: userId, quantity: 2, orderedPrice: 15000 },
    ],
  });

  const res = http.post(`${BASE}${PATH}`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: `POST ${PATH}` },
  });

  const ok = MODE === 'sync'
    ? check(res, {
        'status is 200': (r) => r.status === 200,
        'has order id': (r) => r.body !== null && r.body.length > 0,
      })
    : check(res, {
        'status is 202': (r) => r.status === 202,
        'has sse location': (r) => (r.headers['Location'] || '').includes('/api/v1/order/sse/'),
      });

  if (ok) accepted.add(1);
}

export function handleSummary(data) {
  const ts = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [`order_${MODE}_${ts}.json`]: JSON.stringify(data, null, 2),
  };
}

// ────────────────────────────────────────────────
// 테스트 종료 직후 서버 쪽에서 기록할 것 (k6가 못 재는 것):
//   1. DB 저장 완료량: SELECT COUNT(*) FROM orders;
//      (회차 시작 전 sql/reset-round.sql로 비웠으므로 COUNT 자체가 완료량)
//   2. Executor 큐 소진 시간: 큐 잔여량이 0이 될 때까지 걸린 시간
//      /actuator/metrics/executor.queued?tag=name:insertTaskExecutor 를 5초 간격 폴링 (README 참고)
//   3. 실패/거절 건수: "order create fail" 로그 카운트
// 접수량(k6 iterations) vs 저장 완료량(1) 비교가 비동기 측정의 핵심.
// 참고: insertTaskExecutor는 CallerRunsPolicy라 포화 시 톰캣 스레드가 직접 INSERT를 수행한다.
//       p95가 이중 분포로 나오면 이 백프레셔 동작이 원인 (버그 아님, 해석에 반영).
// ────────────────────────────────────────────────
