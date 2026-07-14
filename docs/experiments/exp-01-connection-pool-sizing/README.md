# exp-01 · 커넥션 풀 상한 정하기 — 부하로 DB 포화점 찾기

> 관련 축: cs-learning **C**(connection pool·transaction duration) + **G**(k6 workload modeling)
> 상태: **계획 (미실행)** — 실행하면 `raw/`에 단계별 결과를 채운다.
> 전제: Hikari(앱 풀) + Neon PgBouncer(transaction pooling)의 **이중 풀** → [architecture/database-and-infra.md](../../architecture/database-and-infra.md)

## 가설

Little's Law(`L = λ × W`, 필요 동시성 = 도착률 × 평균 커넥션 보유시간)는 풀 크기의 **출발점**을 준다. 하지만 DB의 **안전한 커넥션 한계는 계산이 아니라 실제 쿼리 패턴의 부하 테스트로** 정한다. pool size를 올리다 보면 **TPS는 더 늘지 않는데 p95/p99·lock wait·CPU·I/O wait만 증가하는 지점**이 나온다. 그 지점이 DB 포화 구간이고, 운영 상한은 그보다 낮게 잡아 DB를 보호한다.

> 커넥션 풀 상한은 "많이 열수록 빨라지는 값"이 아니라, **DB가 포화되기 직전에서 멈추는 방어선**이다.

## 왜 계산만으로 안 되나

- Little's Law는 "이만큼 동시성이 필요하다"를 주지, "이만큼 열어도 안전하다"를 주지 않는다. `W`(보유시간)는 쿼리·lock·디스크 상태에 따라 요동친다.
- **이중 풀 함정:** Hikari 풀을 크게 잡으면 Neon PgBouncer를 지나 DB server connection만 늘고, lock·I/O 경합으로 **처리량이 오히려 떨어진다**. 앱 풀은 DB가 감당하는 선 아래로 잡아야 한다.

## 고정 조건 (변수는 하나만)

- **대상 경로:** 저장 API 1개(또는 대표 읽기 쿼리 1개) — 한 번에 하나.
- **데이터 크기:** 고정 (예: 링크 10만 건 시드).
- **쿼리 패턴:** 실제 API 시나리오를 k6 스크립트로.
- **환경:** OCI ARM 인스턴스 사양·Neon 플랜 고정.
- **변수:** `Hikari maximumPoolSize` **단계적 증가** (예: 5 → 10 → 20 → 40). 나머지는 모두 고정.

## 절차 (6단계)

1. **목표 RPS를 먼저 정한다** — alpha 예상 도착률 + 여유분.
2. 실제 API 시나리오로 k6 부하를 건다.
3. pool size를 단계적으로 올린다.
4. 매 단계 아래를 **동시에** 본다 (하나만 보면 포화가 숨는다).
5. **TPS가 더 이상 늘지 않는데** p95/p99 latency·lock wait·CPU·I/O wait가 증가하는 지점(= 포화점)을 찾는다.
6. 포화점보다 **한 단계 낮게** pool 상한을 잡고, **connect/statement timeout**과 **circuit breaker**를 둔다.

## 함께 보는 지표

| 지표 | 출처 | 포화 신호 |
|---|---|---|
| TPS(처리량) | k6 | 더 이상 안 오름 |
| p95/p99 latency | k6 | 급증 시작 |
| DB CPU / I/O wait | Neon metrics | 상승 |
| lock wait | `pg_stat_activity`·`EXPLAIN` | 상승 |
| active connection | `pg_stat_activity` | 한계 근접 |
| Hikari active/pending | Micrometer | **pending 급증** = 풀 부족 vs DB 포화 구분 단서 |
| Neon pooler client/server conn | Neon | server conn 포화 |

> `pending`이 급증하는데 DB CPU·lock은 여유면 **풀이 작은 것**, 반대로 풀을 키워도 TPS가 안 오르고 lock·CPU가 오르면 **DB가 포화**다. 이 둘을 가르는 게 이 실험의 핵심.

## 판정 규칙

- **포화점** = TPS가 정체하면서 latency·lock·CPU·I/O 중 다수가 함께 증가하는 pool size.
- **운영 상한** = 포화점보다 한 단계 낮게. + 연결/쿼리 timeout + circuit breaker로 폭주 차단.
- Little's Law 출발점(`λ×W`)과 실측 상한을 **함께 기록**하고, 차이가 나면 왜 나는지(보유시간 변동, 이중 풀) 적는다.

## 함정 (미리 박아두기)

- 앱 풀만 키우고 **Neon PgBouncer server 한계**를 무시 → 이중 풀 경합. ([database-and-infra.md](../../architecture/database-and-infra.md) 2절)
- 외부 HTTP/LLM 호출이 **트랜잭션 안**에 있으면 보유시간 `W`가 왜곡됨 → 트랜잭션 밖으로 뺀 상태에서 측정.
- k6 합성 부하와 실제 사용자 지표를 **섞지 않는다** ([operations 원칙](../../operations/README.md)).

## 남길 증거 (`raw/`에 저장)

- 단계별 k6 요약(원본), 지표 CSV/스크린샷, 포화점 그래프, 최종 상한과 근거 한 줄.
- **면접 한 줄:** "커넥션 풀 상한을 공식으로 정하지 않고, 실제 쿼리로 DB 포화점을 찾아 그 아래로 잡고 timeout·circuit breaker로 DB를 보호했다."
