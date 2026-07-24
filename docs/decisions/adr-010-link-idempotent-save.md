# ADR-010: Link 멱등 저장 — unique constraint + 원자적 upsert

- 날짜: 2026-07-25 / 상태: 확정
- **범주: 아키텍처**
- 관련: [plan-02-link-save-minimal.md](../plan/02-link-save-minimal.md)

## 상황

plan-02(익스텐션 저장 → Link 최소 보존)를 구현하기 전, 위험 로직 1곳을 사람과 논의해 결정했다([plan/README.md](../plan/README.md) 절차). 같은 사용자가 같은 URL을 동시에 여러 번 저장 요청해도(익스텐션 더블클릭, 네트워크 재시도 등) row가 항상 1개여야 한다(멱등) — 이 불변조건을 어떤 메커니즘으로 강제할지가 결정 대상이다.

## 결정 1 — 동시 중복 저장 방지 메커니즘

**결정: `(user_id, canonical_url)` unique constraint + `INSERT ... ON CONFLICT` 원자적 upsert.**

| 선택지 | 트레이드오프 |
|---|---|
| **Unique constraint + INSERT ON CONFLICT** (채택) | DB가 직렬화해 race 자체가 안 생김. 대신 JPA `save()`로 표현 안 되고 native query/QueryDSL insert가 필요 |
| Select-then-insert + 예외 처리 | `save()`로 짜기 쉽지만 조회·삽입 사이 TOCTOU race가 있고, unique constraint 위반 예외를 정상 흐름으로 다뤄야 해 코드가 지저분해짐 |
| Advisory lock + select-then-insert | 예외 기반 흐름 없이 명시적으로 직렬화되지만, 락 범위·해시 충돌 관리라는 새 개념이 추가됨 — 이 규모에 과설계 |

**핵심 이유**: DB 제약이 race를 원천 차단하는 가장 단순한 방식이고, cs-learning C축("unique constraint와 idempotent write")이 원래 겨냥한 학습 목표와도 정확히 일치한다.

## 결정 2 — canonical URL 정규화 범위

**결정: 최소 규칙만 정규화한다 — `scheme`(http→https 통일 여부는 저장된 원본 유지, 비교 키만 정규화), trailing slash, `www.` 유무.**

| 선택지 | 트레이드오프 |
|---|---|
| **최소 규칙(scheme·trailing slash·www)** (채택) | 구현 간단, "명백히 같은 URL"만 묶음. UTM 등 tracking parameter가 다르면 별도 row로 남음 — 나중에 실제 중복 사례가 관측되면 규칙을 넓힘 |
| tracking parameter(`utm_*`, `fbclid` 등)까지 제거 | 더 정확한 중복 판정이지만, 제거 목록을 계속 유지·확장해야 하는 부담이 생김 — conditional-tech-adoption.md 원칙("신호 없이 먼저 만들지 않는다")과 충돌 |

**핵심 이유**: 정규화 규칙은 한 번 넓히는 것보다 좁혀서 시작해 실측 신호로 넓히는 게 안전하다. 규칙을 넓히면(=더 많은 URL을 "같다"고 판단하면) 실제로 다른 콘텐츠를 하나로 합쳐버릴 위험이 있는 반면, 좁게 시작하면 최악의 경우 중복 row가 남는 정도라 되돌리기 쉽다.

## 트레이드오프 종합

- 결정 1로 인해 이 저장 경로만 JPA repository의 표준 `save()` 패턴을 벗어난다 — Link 저장 서비스 계층에 이 예외를 명시적으로 문서화해 다른 도메인에 실수로 전파되지 않게 한다.
- 결정 2는 "완벽한 중복 제거"보다 "명백한 중복만 확실히 제거"를 택한 것이다. tracking parameter가 붙은 동일 URL 재저장 시 row가 늘어나는 걸 완전히 막지는 못한다.

## 재검토 조건

- 실제 사용에서 tracking parameter만 다른 중복 저장이 반복 관측되면 결정 2의 정규화 규칙을 넓힌다.
- `INSERT ON CONFLICT`가 QueryDSL/JPA와 통합하기에 실제로 번거롭다고 판명되면(유지보수 비용이 이득보다 크면) advisory lock 방식으로 전환을 검토한다.

## 면접 답변 요지

> "동시 중복 저장 방지는 예외 처리가 아니라 DB unique constraint + 원자적 upsert로 race 자체를 없앴다. URL 정규화는 처음부터 넓게 잡지 않고 명백한 경우(trailing slash, www)만 좁게 시작했다 — 정규화 규칙을 넓히는 건 되돌리기 어렵지만(다른 콘텐츠를 합쳐버릴 수 있음), 좁게 시작해 나중에 넓히는 건 안전하기 때문이다."
