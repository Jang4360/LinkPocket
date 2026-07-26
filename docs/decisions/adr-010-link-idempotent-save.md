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

> ⚠️ **2026-07-26 회고로 개정.** 아래는 원래(2026-07-25) 결정이다. 회고에서 사람이 범위를 넓혔다 — 개정 내용은 이 절 끝의 "2026-07-26 회고" 참고.

**원래 결정: 최소 규칙만 정규화한다 — `scheme`(http→https 통일 여부는 저장된 원본 유지, 비교 키만 정규화), trailing slash, `www.` 유무.**

| 선택지 | 트레이드오프 |
|---|---|
| **최소 규칙(scheme·trailing slash·www)** (채택) | 구현 간단, "명백히 같은 URL"만 묶음. UTM 등 tracking parameter가 다르면 별도 row로 남음 — 나중에 실제 중복 사례가 관측되면 규칙을 넓힘 |
| tracking parameter(`utm_*`, `fbclid` 등)까지 제거 | 더 정확한 중복 판정이지만, 제거 목록을 계속 유지·확장해야 하는 부담이 생김 — conditional-tech-adoption.md 원칙("신호 없이 먼저 만들지 않는다")과 충돌 |

**핵심 이유**: 정규화 규칙은 한 번 넓히는 것보다 좁혀서 시작해 실측 신호로 넓히는 게 안전하다.

### 2026-07-26 회고 — 범위 확장(사람 주도 재정의)

**문제 정의 (사람 답변)**: "같은 기사라면 당연히 하나로 합쳐져야 한다"가 사용자의 자연스러운 기대다. 다만 정규화를 막연히 넓히면(다른 콘텐츠를 잘못 합침) 그게 진짜 문제다 — 중복 row는 큰 문제가 아니지만, 서로 다른 두 콘텐츠가 하나로 합쳐져 하나가 사라지는 건 훨씬 치명적이다.

**해소**: 이 둘은 모순이 아니다 — **"합쳐도 안전하다고 확신하는 것만" 명시적 목록으로 좁게 추가**하면 된다. "정규화를 넓힌다"가 아니라 "알려진 추적 파라미터라는 좁고 명확한 카테고리 하나를 추가"하는 것.

**선택**: 다음 쿼리 파라미터를 canonical 계산에서 제거한다(그 외 모든 파라미터는 그대로 유지 — 정규화를 계속 좁게 유지하는 것 자체가 핵심):
`utm_source`, `utm_medium`, `utm_campaign`, `utm_term`, `utm_content`, `gclid`, `fbclid`, `msclkid`, `mc_eid`

**관찰 지표**: 이 목록에 없는 추적 파라미터로 인한 미탐지 중복(사용자 피드백/재저장 패턴)이 관측되면 목록을 늘린다. 반대로 서로 다른 콘텐츠가 오탐으로 합쳐지는 사례는 절대 없어야 한다(회귀 테스트로 고정).

## 결정 3 — 저장 취소(un-save) + 조회 (2026-07-26 신규, 사람 주도)

**문제 정의**: 익스텐션 저장 아이콘을 토글(on/off)로 만든다 — 저장돼 있으면 색이 들어오고, 다시 누르면 취소된다. 팝업을 껐다 켜도 이전에 켜둔 상태가 유지돼야 한다.

**선택**: 상태는 클라이언트가 기억하지 않고 **항상 서버에 물어본다**(팝업이 열릴 때마다 조회) — 그러면 "꺼졌다 켜져도 유지"가 별도 로컬 저장 없이 자연히 성립한다.
- `GET /api/links/lookup?url=...`: 그 URL이 이미 저장돼 있는지 조회. `{saved: boolean, linkId: string|null}`.
- `DELETE /api/links/{linkId}`: 저장 취소. 본인 소유가 아니거나 없으면 `404 LINK_NOT_FOUND`(3절 IDOR 원칙과 동일 — 존재 여부 숨김).
- 삭제는 **하드 삭제**(soft-delete 아님) — 취소 후 재저장은 완전히 새 저장으로 취급한다. soft-delete를 하면 `(user_id, canonical_url)` unique constraint에 걸려 재저장이 막히므로, 하드 삭제가 더 단순하고 이 기능 의도(토글)와 정확히 맞는다.
- `POST /api/links` 응답에 `alreadyExisted: boolean` 필드 추가 — 새로 만들어졌는지 이미 있었는지 구분해 "이미 저장했어요" 안내에 쓴다. 구현은 Postgres `RETURNING ... (xmax = 0) AS inserted` 트릭으로 별도 조회 없이 한 문장에서 얻는다.

## 트레이드오프 종합

- 결정 1로 인해 이 저장 경로만 JPA repository의 표준 `save()` 패턴을 벗어난다 — Link 저장 서비스 계층에 이 예외를 명시적으로 문서화해 다른 도메인에 실수로 전파되지 않게 한다.
- 결정 2(개정 후)는 "알려진 추적 파라미터만 확실히 제거"를 택한 것이다 — 목록에 없는 추적 파라미터로 인한 중복은 여전히 남을 수 있지만, 오탐 합병 위험은 없다.

## 재검토 조건

- 목록에 없는 tracking parameter로 인한 중복 저장이 반복 관측되면 목록을 넓힌다.
- `INSERT ON CONFLICT`가 QueryDSL/JPA와 통합하기에 실제로 번거롭다고 판명되면(유지보수 비용이 이득보다 크면) advisory lock 방식으로 전환을 검토한다.

## 면접 답변 요지

> "동시 중복 저장 방지는 예외 처리가 아니라 DB unique constraint + 원자적 upsert로 race 자체를 없앴다. URL 정규화는 처음부터 넓게 잡지 않고 명백한 경우(trailing slash, www)만 좁게 시작했다 — 정규화 규칙을 넓히는 건 되돌리기 어렵지만(다른 콘텐츠를 합쳐버릴 수 있음), 좁게 시작해 나중에 넓히는 건 안전하기 때문이다."
