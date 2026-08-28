// 00. 네트워크 바닥값 측정
// DB를 타지 않는 /ping 엔드포인트로 "네트워크 + 프레임워크 오버헤드"의 바닥을 잰다.
// 이 값이 본 측정치 해석의 기준선이 된다.
//
// 주의: /actuator/health는 DB 커넥션 검증을 수행하므로 바닥값 측정에 쓰지 않는다.
//
// 실행: k6 run -e BASE_URL=http://<서버IP>:8080 00-network-floor.js

import http from 'k6/http';
import { check } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const PATH = __ENV.PATH_ || '/ping';
const RATE = Number(__ENV.RATE || 1000);
const DURATION = __ENV.DURATION || '2m';

export const options = {
  scenarios: {
    floor: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 100,
      maxVUs: 500,
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
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
    [`floor_${ts}.json`]: JSON.stringify(data, null, 2),
  };
}
