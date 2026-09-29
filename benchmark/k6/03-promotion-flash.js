// 03. 선착순 한정 수량 할인 (설계: benchmark/DESIGN-promotion-stock.md)
// 두 시나리오를 한 회차에 함께 돌린다. 전부 open model.
//   attempts : 18:00 정각 가정. LEAD_S초 뒤 시작해 RAMP_S초 만에 초당 PEAK건 → HOLD_S초 유지 → DECAY_S초에 걸쳐 초당 TAIL건까지 감소.
//              시도마다 다른 사용자(1인 1회): 사용자 id = FLASH_USER_FROM + 시나리오 안의 반복 번호. 기본값 합계 약 23.3만 건.
//   base     : 할인 없는 평상시 주문 초당 BASE_RATE건, 회차 전체(LEAD + 시도 + AFTER_S) 동안. 이벤트가 일반 주문에 주는 영향을 본다.
// 환경변수
//   BASE_URL, MODE(async|sync), PROMOTION_ID(필수), TAG, OUT_DIR
//   PEAK(3000) RAMP_S(5) HOLD_S(42) DECAY_S(60) TAIL(300) LEAD_S(30) AFTER_S(300) BASE_RATE(40)
//   FLASH_USER_FROM(1) BASE_USER_FROM(250001) BASE_USER_POOL(50000) SHOP_POOL(1000)
//   PRE_VUS(2000): 급증 순간에 VU를 새로 만들지 않게 미리 만든다. MAX_VUS(4000)
// 전제: users·carts 30만 명(benchmark/sql/seed.sql), 이벤트 생성(POST /api/v1/promotions), 회차마다 이벤트 초기화(POST .../reset).
// 판정·기록은 k6 요약(응답 종류별 건수·지연)과 서버 지표(omp.promotion.confirmed 1초 폴링 → 초당 확정 건수, 재고가 모두 확정된 시각)로 한다.

import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const MODE = (__ENV.MODE || 'async').toLowerCase();
const PROMOTION_ID = Number(__ENV.PROMOTION_ID || 0);
const TAG = __ENV.TAG || 'flash';
const OUT_DIR = (__ENV.OUT_DIR || '.').replace(/[\\/]+$/, '');
const PEAK = Number(__ENV.PEAK || 3000);
const RAMP = Number(__ENV.RAMP_S || 5);
const HOLD = Number(__ENV.HOLD_S || 42);
const DECAY = Number(__ENV.DECAY_S || 60);
const TAIL = Number(__ENV.TAIL || 300);
const LEAD = Number(__ENV.LEAD_S || 30);
const AFTER = Number(__ENV.AFTER_S || 300);
const BASE_RATE = Number(__ENV.BASE_RATE || 40);
const FLASH_USER_FROM = Number(__ENV.FLASH_USER_FROM || 1);
const BASE_USER_FROM = Number(__ENV.BASE_USER_FROM || 250001);
const BASE_USER_POOL = Number(__ENV.BASE_USER_POOL || 50000);
const SHOP_POOL = Number(__ENV.SHOP_POOL || 1000);
const PATH = MODE === 'sync' ? '/api/v1/order' : '/api/v1/order/async';
const ATTEMPT_S = RAMP + HOLD + DECAY;

if (!(PROMOTION_ID > 0)) {
  throw new Error('-e PROMOTION_ID=<이벤트 id> 가 필요하다 (POST /api/v1/promotions 로 만든 값)');
}

// 할인 시도: 응답 종류별 건수와 지연
const promoSuccess = new Counter('promo_success');                 // 동기 200(저장 완료) / 비동기 202(접수)
const promoSoldOut = new Counter('promo_sold_out');                 // 409 SOLD_OUT
const promoAlreadyJoined = new Counter('promo_already_joined');     // 409 ALREADY_JOINED (시도마다 다른 사용자라 0이어야 한다)
const promoNotOpen = new Counter('promo_not_open');                 // 409 NOT_OPEN (0이어야 한다)
const promoQueueFull = new Counter('promo_queue_full');             // 429 대기 자리 없음
const promoFailed = new Counter('promo_failed_other');              // 그 밖 (연결 실패·5xx 등)
const promoSuccessDuration = new Trend('promo_success_duration', true);
const promoSoldOutDuration = new Trend('promo_sold_out_duration', true);
const promoQueueFullDuration = new Trend('promo_queue_full_duration', true);
// 평상시 주문
const baseAccepted = new Counter('base_accepted');
const baseRejected = new Counter('base_rejected');
const baseFailed = new Counter('base_failed_other');
const baseAcceptedDuration = new Trend('base_accepted_duration', true);

export const options = {
  scenarios: {
    attempts: {
      executor: 'ramping-arrival-rate',
      exec: 'attempt',
      startTime: `${LEAD}s`,
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.PRE_VUS || 2000),
      maxVUs: Number(__ENV.MAX_VUS || 4000),
      stages: [
        { target: PEAK, duration: `${RAMP}s` },   // 정각 직후 급증
        { target: PEAK, duration: `${HOLD}s` },   // 몰림 유지
        { target: TAIL, duration: `${DECAY}s` },  // 매진 뒤에도 한동안 시도가 이어진다
      ],
    },
    base: {
      executor: 'constant-arrival-rate',
      exec: 'baseOrder',
      rate: BASE_RATE,
      timeUnit: '1s',
      duration: `${LEAD + ATTEMPT_S + AFTER}s`,
      preAllocatedVUs: 50,
      maxVUs: 500,
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

function hms(ms) {
  const d = new Date(ms);
  const p = (n) => String(n).padStart(2, '0');
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

export function setup() {
  const t0 = Date.now();
  const s1 = t0 + LEAD * 1000;
  console.log(`[run] 시작 ${hms(t0)} MODE=${MODE} TAG=${TAG} PROMOTION_ID=${PROMOTION_ID}`);
  console.log(`[flash] 시도 ${hms(s1)} ~ ${hms(s1 + ATTEMPT_S * 1000)} (최고 초당 ${PEAK}건), 관찰 끝 ${hms(s1 + (ATTEMPT_S + AFTER) * 1000)}`);
  return { t0 };
}

function body(userId, withPromotion) {
  const o = {
    ordererId: userId,
    cartId: userId, // seed.sql: cart N = user N
    shopId: Math.floor(Math.random() * SHOP_POOL) + 1,
    orderMenus: [{ menuId: 1, cartId: userId, quantity: 2, orderedPrice: 15000 }], // 일반 주문과 같은 DB 작업
  };
  if (withPromotion) {
    o.promotionId = PROMOTION_ID;
  }
  return JSON.stringify(o);
}

function post(payload, name) {
  return http.post(`${BASE}${PATH}`, payload, { headers: { 'Content-Type': 'application/json' }, tags: { name } });
}

function isSuccess(res) {
  return MODE === 'sync' ? res.status === 200 : res.status === 202;
}

export function attempt() {
  const userId = FLASH_USER_FROM + exec.scenario.iterationInTest;
  const res = post(body(userId, true), `POST ${PATH} promo`);
  if (isSuccess(res)) {
    promoSuccess.add(1);
    promoSuccessDuration.add(res.timings.duration);
  } else if (res.status === 409) {
    const code = (res.body || '').includes('SOLD_OUT') ? 'SOLD_OUT'
      : (res.body || '').includes('ALREADY_JOINED') ? 'ALREADY_JOINED' : 'NOT_OPEN';
    if (code === 'SOLD_OUT') {
      promoSoldOut.add(1);
      promoSoldOutDuration.add(res.timings.duration);
    } else if (code === 'ALREADY_JOINED') {
      promoAlreadyJoined.add(1);
    } else {
      promoNotOpen.add(1);
    }
  } else if (res.status === 429 || res.status === 503) {
    promoQueueFull.add(1);
    promoQueueFullDuration.add(res.timings.duration);
  } else {
    promoFailed.add(1);
  }
}

export function baseOrder() {
  const userId = BASE_USER_FROM + Math.floor(Math.random() * BASE_USER_POOL);
  const res = post(body(userId, false), `POST ${PATH} base`);
  if (isSuccess(res)) {
    baseAccepted.add(1);
    baseAcceptedDuration.add(res.timings.duration);
  } else if (res.status === 429 || res.status === 503) {
    baseRejected.add(1);
  } else {
    baseFailed.add(1);
  }
}

export function teardown() {
  console.log(`[run] 종료 ${hms(Date.now())} TAG=${TAG}`);
}

export function handleSummary(data) {
  const ts = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [`${OUT_DIR}/promotion_${MODE}_${TAG}_${ts}.json`]: JSON.stringify(data, null, 2),
  };
}

// 회차 절차: 서버 재시작 → 워밍업(일반 주문만) → 큐 소진 → reset-round.sql → POST /api/v1/promotions/{id}/reset → 본측정
// 종료 후: 큐 소진·확인 대기(pendingUnknown) 0 확인 뒤 GET /api/v1/promotions/{id}/check 로 정합성 대조
//   (참여 행 수 ≤ 재고, 매진 시 = 재고, 남은 재고 + 참여 행 수 = 재고, 번호표 중복 0)
