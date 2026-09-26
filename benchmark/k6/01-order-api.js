// 01. 주문 생성 API 부하 테스트
// 기본은 open model: 서버 상태와 무관하게 정해진 유입률로 요청한다 → "초당 N건 유입 시 지연·거절·저장 완료가 어떤가".
//
// MODE=async (기본): POST /api/v1/order/async — 202 + Location 헤더 검증. 응답 = "접수".
// MODE=sync        : POST /api/v1/order       — 200 + orderId 검증.      응답 = "저장 완료".
// 두 엔드포인트가 main에 공존하므로 같은 빌드·같은 스키마에서 전/후를 비교한다.
// 단, 두 응답의 계약(접수 vs 완료)이 다르므로 "저장 속도 개선"으로 확대하지 않는다.
//
// 환경변수
//   SCENARIO   fixed(기본): constant-arrival-rate 로 RATE 유지. B(평상시)·A(용량 계단)·C(지속 초과) 회차.
//              spike      : ramping-arrival-rate. 평상시 → 스파이크 → 평상시 (S 회차). W(필수) = 파일럿 P2에서 잰 저장 처리량(req/s).
//                           기본: 0.5W로 120초 → 5초 만에 2W → 60초 유지 → 5초 만에 0.5W → 300초 관찰 (총 490초).
//                           조정: BASE_RATIO(0.5) SPIKE_RATIO(2) BASE_BEFORE_S(120) SPIKE_RAMP_S(5) SPIKE_HOLD_S(60) SPIKE_DOWN_S(5) BASE_AFTER_S(300)
//                           요청마다 phase 태그(base|spike|after)를 붙여 구간별 접수 지연·거절 수가 요약에 따로 나온다.
//                           시작할 때 구간별 시각을 출력한다 → hist_window.py --from/--to 에 넣어 서버 저장 완료 분포를 계산한다
//                           (k6와 폴링이 같은 데스크탑 시계를 쓰므로 시각이 맞는다).
//              saturate   : constant-vus. VUS(기본 64)명이 쉬지 않고 요청해 서버를 포화시킨다(closed model).
//                           풀 크기 파일럿 P1 전용(README 2절 "풀 크기 산정"). 처리량 = orders_accepted ÷ 시간.
//                           비동기에는 쓰지 않는다(503이 즉시 돌아와 요청이 폭주하므로 P2는 fixed + 높은 RATE).
//              ramp       : ramping-arrival-rate 탐색용(RAMP_STEPS·RAMP·HOLD). 요약 p95는 전체 집계라 판정에는 쓰지 않는다.
//   THRESHOLDS strict(기본): 실패 0, check 100%, p95<200ms, p99<500ms. 두 모드가 모두 감당해야 하는 B 회차용.
//              off        : 판정용 threshold 없음. 포화·용량·스파이크 회차(503·지연 증가가 결과)용. 판정은 카운터·Trend로.
//   TAG        회차 표식(예: S-r1). OUT_DIR 결과 폴더(k6는 폴더를 만들지 않으므로 미리 존재해야 함).
//   MAX_VUS    dropped_iterations 가 뜨면 먼저 늘려볼 값 (기본 fixed 2000, spike·ramp 3000).
//              스파이크·지속 초과의 동기 회차는 응답이 길어져 VU가 모자라 dropped 가 나는 것 자체가 결과이며, 부하기 CPU로 원인을 구분한다.
//
// 스모크:  k6 run -e BASE_URL=http://<서버IP>:8080 -e RATE=10 -e DURATION=30s benchmark/k6/01-order-api.js
// 스파이크: k6 run -e BASE_URL=http://<서버IP>:8080 -e SCENARIO=spike -e W=<W> -e MODE=async -e THRESHOLDS=off \
//              -e TAG=S-r1 -e OUT_DIR=benchmark/results benchmark/k6/01-order-api.js
//
// 전제(sql/seed.sql): users 1..USER_POOL, carts 1..USER_POOL(cart N = user N), shops 1..SHOP_POOL(전부 is_open=1)
//   장바구니-가게 일치 검증은 현재 주석 처리되어 있어 shopId를 랜덤으로 보내도 통과한다. 검증을 켜면
//   seed의 cart N → shop 1+(N mod 1000) 배정과 맞춰야 한다.

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const MODE = (__ENV.MODE || 'async').toLowerCase();              // async | sync
const SCENARIO = (__ENV.SCENARIO || 'fixed').toLowerCase();      // fixed | spike | saturate | ramp
const THRESHOLDS = (__ENV.THRESHOLDS || 'strict').toLowerCase(); // strict | off
const RATE = Number(__ENV.RATE || 1000);
const DURATION = __ENV.DURATION || '15m';
const USER_POOL = Number(__ENV.USER_POOL || 100000);
const SHOP_POOL = Number(__ENV.SHOP_POOL || 1000);
const TAG = __ENV.TAG || SCENARIO;
const OUT_DIR = (__ENV.OUT_DIR || '.').replace(/[\\/]+$/, '');

const PATH = MODE === 'sync' ? '/api/v1/order' : '/api/v1/order/async';

// 스파이크 형태. 비율과 길이의 근거는 README 2절(요구값·시험 설계값) 참고.
const W = Number(__ENV.W || 0);
const SPIKE = {
  base: Math.max(1, Math.round(W * Number(__ENV.BASE_RATIO || 0.5))),
  peak: Math.max(1, Math.round(W * Number(__ENV.SPIKE_RATIO || 2))),
  before: Number(__ENV.BASE_BEFORE_S || 120),
  ramp: Number(__ENV.SPIKE_RAMP_S || 5),
  hold: Number(__ENV.SPIKE_HOLD_S || 60),
  down: Number(__ENV.SPIKE_DOWN_S || 5),
  after: Number(__ENV.BASE_AFTER_S || 300),
};
const PHASES = ['base', 'spike', 'after'];
if (SCENARIO === 'spike' && !(W > 0)) {
  throw new Error('SCENARIO=spike 는 -e W=<파일럿 P2에서 잰 저장 처리량(req/s)> 가 필요하다');
}

// 시도(iterations) / 접수(accepted) / 거절(rejected, 503) / 그 외 실패 를 분리해 기록한다.
const accepted = new Counter('orders_accepted');        // 검증 통과 응답 수. "접수량"은 이 값이다 (iterations 아님)
const rejected = new Counter('orders_rejected');        // 503: insertTaskExecutor 포화로 접수 거절 (백프레셔)
const failedOther = new Counter('orders_failed_other'); // 503 이외의 실패 (4xx, 500, 타임아웃 등)
// 응답 종류별 지연. http_req_duration 은 202와 503이 섞이므로 접수 p95 판정은 order_accepted_duration 으로 한다.
const acceptedDuration = new Trend('order_accepted_duration', true); // 접수(비동기 202) / 저장 완료(동기 200) 응답
const rejectedDuration = new Trend('order_rejected_duration', true); // 503 거절 응답. 거절도 빨라야 백프레셔가 성립한다

function scenario() {
  if (SCENARIO === 'spike') {
    return {
      executor: 'ramping-arrival-rate',
      startRate: SPIKE.base,
      timeUnit: '1s',
      preAllocatedVUs: 300,
      maxVUs: Number(__ENV.MAX_VUS || 3000),
      stages: [
        { target: SPIKE.base, duration: `${SPIKE.before}s` }, // 평상시
        { target: SPIKE.peak, duration: `${SPIKE.ramp}s` },   // 푸시·쿠폰 오픈 직후 급증
        { target: SPIKE.peak, duration: `${SPIKE.hold}s` },   // 스파이크 유지
        { target: SPIKE.base, duration: `${SPIKE.down}s` },   // 가라앉음
        { target: SPIKE.base, duration: `${SPIKE.after}s` },  // 회복·큐 소진 관찰
      ],
    };
  }
  if (SCENARIO === 'saturate') {
    return { executor: 'constant-vus', vus: Number(__ENV.VUS || 64), duration: DURATION };
  }
  if (SCENARIO === 'ramp') {
    const steps = (__ENV.RAMP_STEPS || '500,1000,1500,2000').split(',').map((s) => Number(s.trim()));
    const stages = [];
    for (const target of steps) {
      stages.push({ target, duration: __ENV.RAMP || '1m' });
      stages.push({ target, duration: __ENV.HOLD || '3m' });
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

function phaseAt(elapsedSec) {
  if (elapsedSec < SPIKE.before) return 'base';
  if (elapsedSec < SPIKE.before + SPIKE.ramp + SPIKE.hold + SPIKE.down) return 'spike';
  return 'after';
}

// 항상 통과하는 threshold. k6는 threshold가 걸린 태그 하위 지표만 요약에 보여주므로 구간별 값을 드러내는 용도다.
function phaseReporting() {
  const t = {};
  for (const p of PHASES) {
    t[`order_accepted_duration{phase:${p}}`] = ['p(95)>=0'];
    t[`order_rejected_duration{phase:${p}}`] = ['p(95)>=0'];
    t[`orders_accepted{phase:${p}}`] = ['count>=0'];
    t[`orders_rejected{phase:${p}}`] = ['count>=0'];
    t[`orders_failed_other{phase:${p}}`] = ['count>=0'];
  }
  return t;
}

// 목표를 먼저 선언한다 — 이 선언 자체가 benchmark 문서의 증거가 된다.
const strict = {
  http_req_duration: ['p(95)<200', 'p(99)<500'],
  http_req_failed: ['rate==0'],
  checks: ['rate==1'],
};
const thresholds = Object.assign({}, THRESHOLDS === 'off' ? {} : strict, SCENARIO === 'spike' ? phaseReporting() : {});

export const options = {
  scenarios: { orders: scenario() },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds,
};

function hms(ms) {
  const d = new Date(ms);
  const p = (n) => String(n).padStart(2, '0');
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

// 시작 시각과(스파이크면) 구간 경계를 출력한다. 폴링 파일(server-hist.txt)의 시각과 같은 시계다.
export function setup() {
  const t0 = Date.now();
  console.log(`[run] 시작 ${hms(t0)} MODE=${MODE} SCENARIO=${SCENARIO} TAG=${TAG}`);
  if (SCENARIO === 'spike') {
    const s1 = t0 + SPIKE.before * 1000;
    const s2 = s1 + (SPIKE.ramp + SPIKE.hold + SPIKE.down) * 1000;
    const end = s2 + SPIKE.after * 1000;
    console.log(`[spike] W=${W}, 평상시 ${SPIKE.base}/s, 스파이크 ${SPIKE.peak}/s`);
    console.log(`[spike] base  ${hms(t0)} ~ ${hms(s1)}`);
    console.log(`[spike] spike ${hms(s1)} ~ ${hms(s2)}`);
    console.log(`[spike] after ${hms(s2)} ~ ${hms(end)}`);
    console.log(`[spike] 저장 완료 판정 구간(스파이크 시작 ~ 끝+90초): hist_window.py ... --from ${hms(s1)} --to ${hms(s2 + 90000)} --slo 30`);
  }
  return { t0 };
}

export default function (data) {
  const phase = SCENARIO === 'spike' ? phaseAt((Date.now() - data.t0) / 1000) : null;
  const metricTags = phase ? { phase } : undefined;

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
    tags: phase ? { name: `POST ${PATH}`, phase } : { name: `POST ${PATH}` },
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

  if (ok) {
    accepted.add(1, metricTags);
    acceptedDuration.add(res.timings.duration, metricTags);
  } else if (res.status === 503) {
    rejected.add(1, metricTags);
    rejectedDuration.add(res.timings.duration, metricTags);
  } else {
    failedOther.add(1, metricTags);
  }
}

export function teardown() {
  console.log(`[run] 종료 ${hms(Date.now())} TAG=${TAG}`);
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
// 종료 후 서버 쪽에서 기록할 것 (k6가 못 재는 것):
//   1. 저장 완료 지연: server-hist.txt 의 omp_order_async_completion_seconds 를 hist_window.py 로 구간 계산 (p99 ≤ 30초 판정).
//   2. 커밋 완료량: 잔여 작업 종료(queued=0·active=0) 후 SELECT COUNT(*) FROM orders (verify.sql 3). reset 뒤 측정했으므로 COUNT = 완료량.
//   3. 거절·실패: orders_rejected(503) == 서버 omp.order.async.rejected 증가분, orders_accepted == COUNT(orders) + omp.order.async.failed 증가분.
//      카운터는 기동 후 누적이므로 시작 전 값 대비 증가분을 쓴다.
// 유실 판정은 총건수 점검이다. 누락과 중복이 상쇄될 수 있어 요청별 대조는 별도 과제.
// ────────────────────────────────────────────────
