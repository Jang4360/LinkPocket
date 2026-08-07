# ADR-012: 비동기 AI 파이프라인 — job claim 동시성·트랜잭션 경계·멱등성

- 날짜: 2026-07-27 / 상태: 확정
- 범주: 아키텍처
- 관련: plan-04-async-ai-pipeline(예정), [architecture/async-pipeline.md](../architecture/async-pipeline.md), [conditional-tech-adoption.md](conditional-tech-adoption.md)

## 문제 정의 (사람이 직접 정의 — 사용자 가치·품질 기준)

> 1. 저장이 언젠가는 되어있으면 되며 적어도 1분 내로 검색했을 때 조회가 되어야해
> 2. 비용과 가용성이야. 최종적 정합성만 맞추면돼서 응답시간은 크게 중요하진 않아. 즉 저장 응답은 바로 주지만, 실제로 저장하는 시점은 좀 뒤여도 괜찮다는거지. 그리고 최대한 중복호출로 llm을 여러번 호출하는 비용을 방지해야돼. 또한 ai api의 장애로 인해 혹은 중간 메시지 브로커 장애로인해 이미 저장안 액션이 동작을 안하면 안돼.

즉 지연시간은 느슨하다(≤1분, 최종적 정합성으로 충분). 대신 **① LLM 중복 호출로 인한 비용 낭비를 최소화**하고 **② AI API·인프라 장애로 처리가 영구히 누락되지 않는 가용성**을 지켜야 한다.

## 가설

메시지 브로커 없이 **DB job 테이블 + `SKIP LOCKED` polling worker**로 시작하면, 별도 인프라(브로커) 장애점을 아예 없애 가용성 요구를 만족시키면서도(브로커가 없으니 "브로커 장애"가 성립하지 않음), lease 기반 복구와 호출 전 멱등 조회로 비용 요구(중복 LLM 호출 최소화)를 만족시킬 수 있다. 부하테스트로 실제 처리량·응답시간 문제가 관측되면 그때 Outbox→Kafka로 단계적으로 전환한다([conditional-tech-adoption.md](conditional-tech-adoption.md) 기존 방향과 일치).

---

## 결정 1 — Job claim 동시성 (worker 경쟁)

**결정: `SELECT ... FOR UPDATE SKIP LOCKED`.**

| 선택지 | 트레이드오프 |
|---|---|
| **`SKIP LOCKED` 배치 claim** (채택) | 잠긴 행은 대기 없이 즉시 건너뛰어 다음 행을 잡는다 — lock 대기 자체가 없다. 여러 worker가 한 쿼리로 서로 안 겹치는 N개를 원자적으로 나눠 가질 수 있어, 경쟁 상황에서도 재시도 루프가 생기지 않는다. Postgres 기반 job queue의 표준 관용구 |
| 낙관적 락(status+version 조건부 UPDATE) | 정확성은 동일(둘 다 이중 claim을 막음)하지만, 여러 worker가 같은 "1등 후보"(예: `ORDER BY created_at LIMIT 1`)를 동시에 노려 실패 시 재조회→재UPDATE 왕복이 필요하다. worker 수가 늘수록 이 왕복이 늘어난다 |
| Redis/ShedLock 분산 락 | 여러 노드에서도 안전하지만 별도 인프라(Redis)가 새 장애점이 된다 — "브로커·인프라 장애로 처리 누락"을 막으려는 이번 우선순위와 정면충돌 |

**핵심 이유**: "재시도"의 의미 정리 — 낙관적 락의 재시도는 같은 행을 다시 시도하는 게 아니라 "다른 후보를 다시 찾는 루프"이며, 이 루프 자체가 경쟁 시 비용이다. `SKIP LOCKED`는 애초에 대기하지 않으므로("SKIP" = 잠긴 건 즉시 건너뜀) 이 비교는 "낙관적 락의 재시도 루프 vs SKIP LOCKED의 대기 없는 단일 쿼리"가 되고, 후자가 구조적으로 더 싸다. 다만 현재 규모(worker 수 적음)에서 이 차이가 치명적이진 않다 — 확장성을 감안한 선제 선택.

## 결정 2 — 트랜잭션 경계와 worker 장애 복구

**결정: LLM/HTTP 호출은 DB tx 밖. claim 시 `lease_expires_at`을 같이 기록하고, 복구 스캔이 `PENDING` 또는 `lease_expires_at` 만료된 `PROCESSING`을 함께 대상으로 삼는다.**

| 선택지 | 트레이드오프 |
|---|---|
| **claim(짧은 tx) → 처리(tx 밖) → 완료(짧은 tx), lease 타임아웃으로 복구** (채택) | 외부 호출 중 DB lock을 안 잡아 가용성이 좋다. claim 자체가 커밋 전에 실패하면(worker가 짧은 claim tx 도중 죽음) DB가 자동 롤백해 즉시 원상복구되고(`idle_in_transaction_session_timeout`으로 hang 케이스까지 보강), claim 이후(LLM 호출 중) 죽는 경우는 lease 만료로 다른 worker가 재claim한다 — 실패 시점에 따라 두 메커니즘이 역할을 나눠 가진다 |
| 처리 전체를 하나의 긴 트랜잭션으로 | 외부 호출 동안 DB 커넥션·row lock을 잡아먹어 다른 worker의 처리량이 줄고, AI API가 느려지면 그 지연이 그대로 DB에 전가된다 — 가용성 우선순위와 충돌 |
| lease 없이 status만으로 복구 | `PROCESSING`이 "정상 작업 중"인지 "worker가 죽어 버려짐"인지 구분할 신호가 없어, 죽은 job이 영원히 방치될 수 있다 — 가용성 요구(AI API·worker 장애에도 처리 누락 없음)를 못 지킴 |

**핵심 이유**: `lease_expires_at`은 "이 worker가 늦어도 이 시각까지 끝내겠다"는 자기만료형 약속이다. 이게 없으면 상태값만으로는 살아있는 작업과 버려진 작업을 구분 못 해, AI API 장애·worker 크래시 시 job이 영구히 갇힐 수 있다 — 이번 우선순위가 명시한 "장애로 인해 저장 안 액션이 동작 안 하면 안 된다"를 정확히 이게 지킨다.

## 결정 3 — 멱등성 (LLM 중복 호출·비용 방지)

**결정: `(linkId, inputHash, modelVersion)` unique constraint를 가진 결과 테이블 + **호출 전** 존재 여부 조회.**

| 선택지 | 막는 범위 |
|---|---|
| unique constraint만 | 결과가 중복 **저장**되는 것은 막지만, INSERT 시점에야 걸리므로 두 worker가 이미 각자 LLM을 호출한 **뒤**다 — 비용은 이미 두 번 나간 상태. 데이터 정합성은 지키지만 비용 방지는 아니다 |
| **호출 전 조회 + unique constraint** (채택) | 호출 전 `(linkId, inputHash, modelVersion)`로 기존 결과가 있는지 먼저 확인해 있으면 LLM을 아예 안 부르고 재사용한다 — 이게 비용을 실제로 아끼는 층. unique constraint는 그래도 두 worker가 동시에 호출·INSERT를 시도하는 극단적 race에서 데이터가 두 벌 남지 않게 하는 최후 방어선 |
| LLM provider의 idempotency key 의존 | 대부분의 LLM API가 이런 멱등 키를 지원/보장하지 않아 신뢰할 수 없다 |

**핵심 이유**: 결정 1(claim 배타성)과 결정 2(lease)가 "애초에 중복 호출이 거의 안 일어나게" 막고, 호출 전 조회가 "그래도 혹시 몰라" 비용을 아끼고, unique constraint가 그 둘이 다 뚫려도 데이터 정합성만은 지키는 3중 방어 구조다. 비용을 우선순위로 명시했기 때문에 "저장 시점에만 막는" 방식으로는 부족하다고 판단했다.

---

## 관찰 지표

- 저장 완료 → 검색 가능까지 걸린 시간(P95) — 목표 ≤ 1분
- LLM 호출 횟수 대비 실제 고유 job 수 비율(중복 호출률) — 목표 0에 근접
- lease 만료로 인한 재claim 발생 횟수·주기
- worker 강제 종료 후 job이 실제로 복구되기까지 걸린 시간
- `SKIP LOCKED` polling 부하(쿼리 빈도·DB CPU) — Outbox/Kafka 전환 신호 판단 근거

## 결과 (구현·운영 후 채움)

미측정 — plan-04 구현 전.

## 재검토 조건

- 부하테스트에서 polling 기반 처리량·응답시간이 기준을 못 맞추면 [conditional-tech-adoption.md](conditional-tech-adoption.md)의 다음 단계(Outbox→Kafka)를 검토한다.
- worker 수가 크게 늘어 `SKIP LOCKED`의 이점(경쟁 낭비 없음)이 실측으로 유의미해지거나, 반대로 낙관적 락과 차이가 없다고 실측되면 재비교한다.
- lease 타임아웃 값이 실제 LLM 호출 지연 분포와 안 맞아 정상 job이 자주 재claim되면(오탐) 값을 조정한다.
