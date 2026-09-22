// 01. 주문 생성 API 부하 테스트 (open model)
// 서버 상태와 무관하게 초당 RATE건을 계속 발사한다 → "초당 N건 유입 시 p95/p99가 얼마인가".
//
// MODE=async (기본): POST /api/v1/order/async — 202 + Location 헤더 검증. 응답 = "접수".
// MODE=sync        : POST /api/v1/order       — 200 + orderId 검증.      응답 = "저장 완료".
// 두 엔드포인트가 main에 공존하므로 같은 빌드·같은 스키마에서 전/후를 비교한다.
// 단, 두 응답의 계약(접수 vs 완료)이 다르므로 "저장 속도 개선"으로 확대하지 않는다.
//
// 환경변수
//   SCENARIO   fixed(기본): constant-arrival-rate 로 RATE 유지. 지연·에러율 비교용.
//              ramp       : ramping-arrival-rate. RAMP_STEPS(기본 500,1000,1500,2000)의 각 rate로
//                           RAMP(기본 1m) 동안 올리고 HOLD(기본 3m) 동안 유지. 용량 탐색용.
//                           k6 summary의 p95는 전체 집계라 단계별 값은 --out csv 시계열로 계산해야 한다.
//                           확정 수치는 후보 rate에서 SCENARIO=fixed 를 따로 돌려 만든다.
//   THRESHOLDS strict(기본): 실패 0, check 100%, p95<200ms, p99<500ms. 개선 후 정상 동작 회차용.
//              off        : threshold 없음. 포화·용량 탐색 회차(503이 나는 게 정상)용. 판정은 카운터로.
//   TAG        회차 표식(예: r1). OUT_DIR 결과 폴더(k6는 폴더를 만들지 않으므로 미리 존재해야 함).
//   MAX_VUS    dropped_iterations 가 뜨면 먼저 늘려볼 값 (기본 2000, ramp 3000).
//
// 스모크:  k6 run -e BASE_URL=http://<서버IP>:8080 -e RATE=10 -e DURATION=30s 01-order-api.js
// 본측정:  k6 run -e BASE_URL=http://<서버IP>:8080 -e RATE=1000 -e DURATION=15m -e TAG=r1 -e OUT_DIR=benchmark/results \
//              --out csv=benchmark/results/order_async_r1.csv 01-order-api.js
// 동기측정: 위 명령에 -e MODE=sync 추가 (동기 회차는 p95 threshold가 깨지는 것 자체가 비교 결과)
//
// 전제(sql/seed.sql): users 1..USER_POOL, carts 1..USER_POOL(cart N = user N), shops 1..SHOP_POOL(전부 is_open=1)
//   장바구니-가게 일치 검증은 현재 주석 처리되어 있어 shopId를 랜덤으로 보내도 통과한다. 검증을 켜면
//   seed의 cart N → shop 1+(N mod 1000) 배정과 맞춰야 한다.

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const MODE = (__ENV.MODE || 'async').toLowerCase();              // async | sync
const SCENARIO = (__ENV.SCENARIO || 'fixed').toLowerCase();      // fixed | ramp
const THRESHOLDS = (__ENV.THRESHOLDS || 'strict').toLowerCase(); // strict | off
const RATE = Number(__ENV.RATE || 1000);
const DURATION = __ENV.DURATION || '15m';
const USER_POOL = Number(__ENV.USER_POOL || 100000);
const SHOP_POOL = Number(__ENV.SHOP_POOL || 1000);
const TAG = __ENV.TAG || SCENARIO;
const OUT_DIR = (__ENV.OUT_DIR || '.').replace(/[\\/]+$/, '');

const PATH = MODE === 'sync' ? '/api/v1/order' : '/api/v1/order/async';

// 시도(iterations) / 접수(accepted) / 거절(rejected, 503) / 그 외 실패 를 분리해 기록한다.
const accepted = new Counter('orders_accepted');        // 검증 통과 응답 수. "접수량"은 이 값이다 (iterations 아님)
const rejected = new Counter('orders_rejected');        // 503: insertTaskExecutor 포화로 접수 거절 (백프레셔)
const failedOther = new Counter('orders_failed_other'); // 503 이외의 실패 (4xx, 500, 타임아웃 등)

function scenario() {
  if (SCENARIO === 'ramp') {
    const steps = (__ENV.RAMP_STEPS || '500,1000,1500,2000').split(',').map((s) => Number(s.trim()));
    const stages = [];
    for (const target of steps) {
      stages.push({ target, duration: __ENV.RAMP || '1m' }); // 상승
      stages.push({ target, duration: __ENV.HOLD || '3m' }); // 유지 — 이 구간의 p95·503을 단계 값으로 읽는다
    }
    return {
      executor: 'ramping-arrival-rate',
      startRate: Number(__ENV.START_RATE || 200),
      timeUnit: '1s',
      preAllocatedVUs: 300,
      maxVUs: Number(__ENV.MAX_VUS || 3000),
      stages,
    };
  }
  return {
    executor: 'constant-arrival-rate',
    rate: RATE,
    timeUnit: '1s',
    duration: DURATION,
    preAllocatedVUs: 300,
    maxVUs: Number(__ENV.MAX_VUS || 2000),
  };
}

// 목표를 먼저 선언한다 — 이 선언 자체가 benchmark 문서의 증거가 된다.
const thresholds = THRESHOLDS === 'off' ? {} : {
  http_req_duration: ['p(95)<200', 'p(99)<500'],
  http_req_failed: ['rate==0'],
  checks: ['rate==1'],
};

export const options = {
  scenarios: { orders: scenario() },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds,
};

export default function () {
  const userId = Math.floor(Math.random() * USER_POOL) + 1;
  const payload = JSON.stringify({
    ordererId: userId,
    cartId: userId, // seed.sql이 cart N = user N 으로 적재
    shopId: Math.floor(Math.random() * SHOP_POOL) + 1,
    orderMenus: [
      { menuId: 1, cartId: userId, quantity: 2, orderedPrice: 15000 }, // order_menu.menu_id 는 FK가 아니라 menus 시딩 불필요
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
  else if (res.status === 503) rejected.add(1);
  else failedOther.add(1);
}

export function handleSummary(data) {
  const ts = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [`${OUT_DIR}/order_${MODE}_${TAG}_${ts}.json`]: JSON.stringify(data, null, 2),
  };
}

// ────────────────────────────────────────────────
// 회차 절차(README 3절): 재시작 → 워밍업 → 워밍업 작업 소진 확인(queued=0·active=0) → reset-round.sql → 시작 전 값 기록 → 본측정
// 테스트 종료 후 서버 쪽에서 기록할 것 (k6가 못 재는 것):
//   1. 완료 시점: insertTaskExecutor 의 executor.queued=0 이고 executor.active=0 이 된 첫 시각 (README 4절 폴링).
//      큐(100)는 1초 안에 비므로 "큐 소진 시간"은 지표로서 의미가 거의 없다. 완료 시점까지의 지연과 거절 건수를 기록한다.
//   2. 커밋 완료량: 완료 시점 이후 SELECT COUNT(*) FROM orders (verify.sql 3). reset 뒤 측정했으므로 COUNT = 완료량.
//   3. 거절: orders_rejected(503) == 서버 omp.order.async.rejected 의 **증가분**(시작 전 값 대비). 카운터는 기동 후 누적이다.
//      실패: "order create fail" 로그 카운트 (접수 후 INSERT 실패).
// 유실 판정: orders_accepted(202) == completed_orders 는 총건수 점검이다. 누락과 중복이 상쇄될 수 있어 요청별 대조는 3단계.
// 503 비율이 오르기 시작하는 rate = "측정 조건에서 거절 없이 유지한 유입률" (dropped_iterations·p95·다른 오류도 함께 본다).
// ────────────────────────────────────────────────
