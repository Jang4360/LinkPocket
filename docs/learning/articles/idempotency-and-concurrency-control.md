---
주제: 멱등성·동시성 제어 메커니즘
관련 plan: [plan/01-auth-google-oauth.md](../../plan/01-auth-google-oauth.md), [plan/02-link-save-minimal.md](../../plan/02-link-save-minimal.md), [plan/03-safe-fetch-extract.md](../../plan/03-safe-fetch-extract.md)
cs-learning 축: C. DB 모델·트랜잭션·실행 계획 / unique constraint와 idempotent write
작성일: 2026-07-25 / 상태: ADR 반영 완료
---

익스텐션에서 "저장" 버튼을 두 번 빠르게 눌렀다(더블클릭, 또는 네트워크 재시도로 같은 요청이 두 번 나감). 서버에 같은 URL 저장 요청이 거의 같은 순간에 두 번 도착한다. 아무 방어도 안 하면 무슨 일이 생기는가?

```sql
-- 순진한 구현
select * from link where user_id = ? and canonical_url = ?;
-- 결과가 없으면
insert into link (...) values (...);
```

두 요청이 거의 동시에 이 코드를 타면, **둘 다** `select` 결과가 없는 걸 보고 **둘 다** `insert`한다. row가 2개 생긴다. 이게 멱등성이 깨지는 순간이다.

> 멱등성·동시성 제어는 코드를 빠르게 만드는 기술이 아니라, "동시에 벌어진 일"을 서버가 하나의 일관된 이야기로 만드는 일이다.

## 왜 필요한가

서버는 요청을 하나씩 순서대로 처리한다고 착각하기 쉽다. 실제로는 여러 요청이 정확히 겹치는 시간에 같은 데이터를 읽고 쓸 수 있다. 이때 "먼저 읽고 나중에 쓰는" 코드는 항상 위험하다 — **읽은 시점과 쓰는 시점 사이의 틈**에 다른 요청이 끼어들 수 있기 때문이다. 이 틈을 `TOCTOU`(Time-Of-Check to Time-Of-Use, 확인한 시점과 사용하는 시점의 어긋남)라 부른다. 멱등성(같은 요청을 여러 번 보내도 결과가 한 번 보낸 것과 같음)과 동시성 제어(여러 요청이 동시에 같은 자원을 건드릴 때 정합성을 지키는 것)는 결국 **이 틈을 없애거나, 틈이 있어도 안전하게 만드는 방법들의 이름**이다.

## 이 문제를 푸는 방법들

**Unique constraint — DB 스키마가 직접 막는다.**
"이 조합의 row는 절대 두 개일 수 없다"를 테이블 정의 자체에 못박는다. 두 트랜잭션이 동시에 `insert`를 시도해도, DB가 나중 트랜잭션을 원자적으로 처리해 row가 절대 2개가 되지 않는다. **아직 존재하지 않던 row를 "딱 하나만 만드는" 문제**에 가장 잘 맞는다.

```sql
-- LinkPocket plan-02: unique(user_id, canonical_url) + 원자적 upsert
insert into link (id, user_id, url, canonical_url, status, created_at)
values (gen_random_uuid(), ?, ?, ?, 'PENDING', now())
on conflict (user_id, canonical_url) do update set url = link.url
returning id, canonical_url, status;
```

**비관적 락(pessimistic lock) — 먼저 온 쪽이 문을 잠근다.**
row를 읽는 순간 DB에 "내가 다 쓸 때까지 아무도 이 row를 못 건드리게 해달라"고 요청한다(`SELECT ... FOR UPDATE`). 다른 트랜잭션은 그 row에 접근하려는 순간 대기한다. **이미 존재하는 row를 정확히 한 번만 처리해야 하는 문제**에 맞는다.

```java
// LinkPocket plan-01: 동시 refresh 요청을 직렬화
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<RefreshToken> findByTokenHash(String tokenHash);
```

**낙관적 락(optimistic lock) — 나중에 충돌을 알아챈다.**
row에 버전 번호를 두고, 수정할 때 "내가 읽은 버전이 아직 유효한가"를 함께 확인한다(`UPDATE ... WHERE version = ?`). 버전이 달라졌으면(다른 트랜잭션이 먼저 손댔으면) 실패로 취급하고 재시도한다. 락을 오래 붙잡지 않아 처리량이 좋지만, **아직 버전이 없는(=처음 만드는) row에는 애초에 쓸 수 없다** — 이게 앞선 대화에서 나온 질문("왜 unique constraint만 추천했나")의 답이다. Link 최초 저장은 "새로 만드는" 문제라 낙관적 락이 성립하지 않는다.

**원자적 조건부 UPDATE — "조회→판단→수정" 세 단계를 한 문장으로 합친다.**
확인과 변경 사이에 아무도 못 끼어들게, DB 문장 하나로 묶는다. 낙관적 락의 "버전 번호" 대신 **상태값 자체를 조건**으로 쓰는 특수한 형태로 볼 수 있다.

```sql
-- LinkPocket plan-03: 같은 Link에 대한 중복 fetch 시도 방지(선점)
update link set status = 'FETCHING'
where id = ? and status = 'PENDING';
-- 영향받은 row가 0개면 "이미 다른 호출이 처리 중"이라는 뜻 — 그대로 반환
```

**Advisory lock — row가 아니라 "이름"에 락을 건다.**
아직 row가 없거나, 여러 row에 걸친 작업을 직렬화하고 싶을 때 임의의 키(문자열/숫자)로 락을 잡는다. 유연하지만 "이 락이 무엇을 지키는지"가 스키마에는 안 보이고 코드에만 있어서, 락 없이 접근하는 경로를 실수로 만들기 쉽다.

## 후보 비교

| 선택지 | 무엇을 보장하는가 | 무엇을 안 보장하는가 / 비용 | 이 프로젝트에서 언제 맞는가 |
|---|---|---|---|
| Unique constraint | 같은 키의 row가 절대 2개 안 됨 | "이미 있는 row를 한 번만 고치기"엔 안 맞음(문제 자체가 다름) | **처음 생성**하는 row의 중복 방지(plan-02) |
| 비관적 락 | 락을 쥔 동안 다른 트랜잭션 완전 차단 | 락 대기 시간이 늘고, 잘못 쓰면 데드락 | **이미 있는 row**를 정확히 한 번만 소비(plan-01) |
| 낙관적 락(버전) | 충돌을 감지해 재시도하게 함, 락 대기 없음 | 충돌이 잦으면 재시도 비용 커짐, 신규 row엔 적용 불가 | 갱신 충돌이 드문 기존 row 수정(plan-04 후보) |
| 원자적 조건부 UPDATE | 확인+변경 사이 틈을 원천 차단 | 조건이 상태값 하나로 표현 가능해야 함 | **상태 전이 자체가 자물쇠**인 경우(plan-01 소비, plan-03 선점) |
| Advisory lock | row 존재와 무관하게 임의 범위를 직렬화 | 스키마에 안 보이는 암묵적 규칙이 됨 | 지금 LinkPocket엔 없음 — 여러 row를 아우르는 배치 작업이 생기면 후보 |

## 어디서 무너지는가

가장 흔한 실수는 이 문서 맨 위 예시처럼 "조회 → 없으면 → 삽입/수정"을 그대로 코드로 짜는 것이다(TOCTOU). 이건 로컬에서 요청을 하나씩 테스트할 때는 절대 안 잡히고, 실제 동시 트래픽에서만 드러난다 — 그래서 계약 테스트가 `CountDownLatch`로 진짜 동시 요청을 재현해야 한다(순차 실행 테스트로는 이 클래스의 버그를 못 잡는다, `LinkIdempotentSaveContractTest` 참고). 특히 AI가 생성한 코드에서 자주 나는 실패이기도 하다 — "일단 동작하는" select-then-insert가 가장 직관적으로 보이기 때문이다.

## 무엇을 보고 판단하는가

동시 요청 부하 테스트에서 관측되는 row 중복 건수, 재시도로 인한 지연(p95), 락 대기 시간(`lock_wait_time`), 낙관적 락을 쓴다면 `optimistic_lock_retry_count`. "단일 요청 테스트가 전부 통과"만 보면 이 문제는 완전히 숨는다 — 반드시 동시성 테스트가 별도로 있어야 한다.

## LinkPocket 적용 사례

- **plan-01(auth)**: refresh token 소비 — 비관적 락(`PESSIMISTIC_WRITE`)으로 동시 refresh 요청을 직렬화하고, family 폐기가 롤백되지 않도록 `@Transactional(noRollbackFor = ...)`도 함께 처리 → [ADR-006](../../decisions/adr-006-auth-session-architecture.md)
- **plan-02(link save)**: 동시 중복 저장 방지 — unique constraint + `INSERT ON CONFLICT` 원자적 upsert → [ADR-010](../../decisions/adr-010-link-idempotent-save.md)
- **plan-03(safe fetch)**: 중복 fetch 시도 방지 — `link.status` 원자적 조건부 UPDATE로 선점 → [ADR-011](../../decisions/adr-011-safe-fetch-ssrf-timeout-retry.md)

## Claude 추천 · ADR로 넘길 질문

LinkPocket 규모(개인 프로젝트, 낮은 트래픽)에서는 **원자적 조건부 UPDATE/upsert를 기본값으로 삼고, 이미 있는 row를 여러 필드에 걸쳐 자주 갱신해야 할 때만 낙관적 락으로 넘어가는 순서**를 추천한다. 비관적 락은 "반드시 한 번만 일어나야 하는" 짧은 트랜잭션(토큰 소비처럼)에만 국한한다 — 오래 잡을수록 락 대기가 다른 요청을 줄줄이 막기 때문이다.

**락을 오래 잡을수록 정합성은 강해지지만 처리량은 떨어진다 — 이 저울을 어느 쪽으로 기울일지는 실제 트래픽 패턴을 실측하기 전엔 확신할 수 없다.**

- 사람과 논의해 정할 것: plan-04에서 job claim(`SKIP LOCKED`)을 도입할 때, 지금까지의 패턴(조건부 UPDATE)과 `SKIP LOCKED` 중 어느 쪽을 기본으로 삼을지.
