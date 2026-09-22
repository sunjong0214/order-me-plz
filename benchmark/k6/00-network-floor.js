// 00. 네트워크 바닥값 측정
// DB를 타지 않는 /ping 엔드포인트로 "네트워크 + 프레임워크 오버헤드"의 바닥을 잰다.
// 이 값은 API 측정치를 읽을 때의 참고선이다. 서로 다른 분포의 백분위수는 뺄셈이 성립하지 않으므로
// "API p95 − ping p95 = 서버 처리 시간" 같은 계산에는 쓰지 않는다.
//
// 주의: /actuator/health는 DB 커넥션 검증을 수행하므로 바닥값 측정에 쓰지 않는다.
//
// 실행: k6 run -e BASE_URL=http://<서버IP>:8080 -e OUT_DIR=benchmark/results 00-network-floor.js
// 환경변수: RATE(기본 1000) DURATION(기본 2m) PATH_(기본 /ping) TAG(기본 floor) OUT_DIR(기본 . , 미리 존재해야 함)

import http from 'k6/http';
import { check } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const PATH = __ENV.PATH_ || '/ping';
const RATE = Number(__ENV.RATE || 1000);
const DURATION = __ENV.DURATION || '2m';
const TAG = __ENV.TAG || 'floor';
const OUT_DIR = (__ENV.OUT_DIR || '.').replace(/[\\/]+$/, '');

export const options = {
  scenarios: {
    floor: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 100,
      maxVUs: Number(__ENV.MAX_VUS || 500),
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: {
    // 바닥값 측정은 실패가 0이어야 한다. 1건이라도 있으면 네트워크·서버 상태부터 점검.
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
  },
};

export default function () {
  const res = http.get(`${BASE}${PATH}`, { tags: { name: `GET ${PATH}` } });
  check(res, { 'status 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
  const ts = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [`${OUT_DIR}/${TAG}_${ts}.json`]: JSON.stringify(data, null, 2),
  };
}
