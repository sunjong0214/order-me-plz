#!/usr/bin/env python3
"""구간별 지연 분포 계산 (Prometheus 누적 히스토그램 스냅샷 → 임의 구간의 분포).

README 4절 폴링이 5초마다 /actuator/prometheus 의 버킷 줄을 "HH:MM:SS <샘플 줄>" 형식으로 server-hist.txt 에 남긴다.
버킷은 누적값이므로 두 시점의 차이가 그 구간에 끝난 작업의 분포다.
  - 구간 시작 = 시작 시각 이전의 마지막 스냅샷, 구간 끝 = 끝 시각 이후의 첫 스냅샷 (폴링 간격만큼 넓어질 수 있음)
  - 백분위수는 버킷 안에서 선형 보간한 추정값이다 (Prometheus histogram_quantile 과 같은 방식). 정확한 값은 버킷 경계에서만 보장된다.
  - --slo 초 이하 비율은 경계값(예: 30)이 버킷 경계와 같으면 정확한 값이다. "p99 ≤ 30초" ⇔ "30초 이하 비율 ≥ 99%".

사용 예:
  python benchmark/tools/hist_window.py server-hist.txt --metric omp_order_async_completion_seconds \
      --label outcome=completed --from 14:03:00 --to 14:05:30 --slo 30
  python benchmark/tools/hist_window.py --selftest
"""
import argparse
import math
import re
import sys

LINE = re.compile(r'^(\d{2}:\d{2}:\d{2}) ([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{([^}]*)\})? (\S+)')
LABEL = re.compile(r'(\w+)="([^"]*)"')


def to_seconds(hms):
    h, m, s = (int(x) for x in hms.split(':'))
    return h * 3600 + m * 60 + s


def parse(lines, metric, label_filter):
    """{시각(초): {le(float): 누적 건수}} — 지정한 metric 의 _bucket 줄 중 라벨 조건이 맞는 것만."""
    snapshots = {}
    for raw in lines:
        m = LINE.match(raw.strip())
        if not m:
            continue
        hms, name, labels, value = m.groups()
        if name != metric + '_bucket':
            continue
        lab = dict(LABEL.findall(labels or ''))
        if any(lab.get(k) != v for k, v in label_filter.items()):
            continue
        le = math.inf if lab['le'] == '+Inf' else float(lab['le'])
        snapshots.setdefault(to_seconds(hms), {})[le] = float(value)
    return snapshots


def pick(snapshots, start, end):
    times = sorted(snapshots)
    if not times:
        raise SystemExit('해당 metric/라벨의 버킷 줄이 없다. --metric 이름(_bucket 제외)과 --label 을 확인할 것')
    before = [t for t in times if t <= start]
    after = [t for t in times if t >= end]
    t0 = before[-1] if before else times[0]
    t1 = after[0] if after else times[-1]
    return t0, t1


def window(snapshots, t0, t1):
    a, b = snapshots[t0], snapshots[t1]
    delta = {le: b.get(le, 0.0) - a.get(le, 0.0) for le in sorted(set(a) | set(b))}
    if any(v < 0 for v in delta.values()):
        raise SystemExit('구간 안에서 누적값이 줄었다 (서버 재시작?). 같은 기동 안의 구간만 계산할 수 있다')
    return delta


def quantile(delta, q):
    total = delta.get(math.inf, 0.0)
    if total == 0:
        return None
    rank = q * total
    prev_le, prev_cum = 0.0, 0.0
    for le in sorted(delta):
        cum = delta[le]
        if cum >= rank:
            if math.isinf(le):
                return math.inf   # 마지막 유한 경계보다 큼
            if cum == prev_cum:
                return le
            return prev_le + (le - prev_le) * (rank - prev_cum) / (cum - prev_cum)
        prev_le, prev_cum = le, cum
    return math.inf


def fraction_at_most(delta, seconds):
    total = delta.get(math.inf, 0.0)
    if total == 0:
        return None, False
    bounds = sorted(le for le in delta if not math.isinf(le))
    if seconds in delta:
        return delta[seconds] / total, True
    prev_le, prev_cum = 0.0, 0.0
    for le in bounds:
        if le > seconds:
            est = prev_cum + (delta[le] - prev_cum) * (seconds - prev_le) / (le - prev_le)
            return est / total, False
        prev_le, prev_cum = le, delta[le]
    return prev_cum / total, False


def fmt(sec):
    if sec is None:
        return '측정 불가(0건)'
    if math.isinf(sec):
        return '> 마지막 경계'
    return f'{sec * 1000:.1f} ms' if sec < 1 else f'{sec:.2f} s'


def report(delta, t0, t1, slo):
    total = delta.get(math.inf, 0.0)
    print(f'구간 스냅샷: {t0 // 3600:02d}:{t0 % 3600 // 60:02d}:{t0 % 60:02d} → '
          f'{t1 // 3600:02d}:{t1 % 3600 // 60:02d}:{t1 % 60:02d}  ({t1 - t0}초)')
    print(f'완료 건수: {total:.0f}')
    for q in (0.5, 0.95, 0.99):
        print(f'p{int(q * 100)} (보간 추정): {fmt(quantile(delta, q))}')
    if slo is not None:
        frac, exact = fraction_at_most(delta, slo)
        if frac is None:
            print(f'{slo:g}초 이하 비율: 측정 불가(0건)')
        else:
            verdict = '충족' if frac >= 0.99 else '미충족'
            print(f'{slo:g}초 이하 비율: {frac * 100:.3f}% ({"정확값" if exact else "보간 추정"}) → p99 ≤ {slo:g}초 {verdict}')
    print('누적 분포 (le: 건수 / 비율)')
    for le in sorted(delta):
        name = '+Inf' if math.isinf(le) else f'{le:g}s'
        print(f'  {name:>7}: {delta[le]:>10.0f} / {delta[le] / total * 100 if total else 0:6.2f}%')


def selftest():
    metric = 'omp_order_async_completion_seconds'
    def snap(t, counts):
        les = ['0.1', '1.0', '10.0', '30.0', '+Inf']
        return [f'{t} {metric}_bucket{{le="{le}",outcome="completed"}} {c}' for le, c in zip(les, counts)] + \
               [f'{t} {metric}_bucket{{le="{le}",outcome="failed"}} 999' for le in les]
    lines = snap('10:00:00', [10, 10, 10, 10, 10]) + snap('10:00:05', [10, 10, 10, 10, 10]) + \
        snap('10:01:05', [60, 90, 100, 108, 110])
    s = parse(lines, metric, {'outcome': 'completed'})
    t0, t1 = pick(s, to_seconds('10:00:03'), to_seconds('10:01:00'))
    assert (t0, t1) == (to_seconds('10:00:00'), to_seconds('10:01:05')), (t0, t1)
    d = window(s, t0, t1)   # 구간 증가분: le0.1=50, le1=80, le10=90, le30=98, Inf=100
    assert d[math.inf] == 100 and d[30.0] == 98
    assert abs(quantile(d, 0.5) - 0.1) < 1e-9               # 50번째 = 0.1s 경계
    assert abs(quantile(d, 0.95) - (10 + 20 * 5 / 8)) < 1e-9  # 10~30s 버킷 안 보간 = 22.5s
    assert quantile(d, 0.99) == math.inf                    # 99번째는 +Inf 버킷
    frac, exact = fraction_at_most(d, 30)
    assert exact and abs(frac - 0.98) < 1e-9
    frac, exact = fraction_at_most(d, 20)
    assert not exact and abs(frac - (90 + 8 * 10 / 20) / 100) < 1e-9
    print('selftest OK')


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('file', nargs='?')
    p.add_argument('--metric', help='예: omp_order_async_completion_seconds (_bucket 제외)')
    p.add_argument('--label', action='append', default=[], help='라벨 조건 key=value (여러 번 가능)')
    p.add_argument('--from', dest='start', help='구간 시작 HH:MM:SS')
    p.add_argument('--to', dest='end', help='구간 끝 HH:MM:SS')
    p.add_argument('--slo', type=float, help='이 초 이하로 끝난 비율과 p99 판정 (예: 30)')
    p.add_argument('--selftest', action='store_true')
    a = p.parse_args()
    if a.selftest:
        selftest()
        return
    if not (a.file and a.metric and a.start and a.end):
        p.error('file, --metric, --from, --to 가 필요하다')
    label_filter = dict(kv.split('=', 1) for kv in a.label)
    with open(a.file, encoding='utf-8') as f:
        snapshots = parse(f, a.metric, label_filter)
    t0, t1 = pick(snapshots, to_seconds(a.start), to_seconds(a.end))
    report(window(snapshots, t0, t1), t0, t1, a.slo)


if __name__ == '__main__':
    sys.exit(main())
