---
기능: 링크 최소 저장 (익스텐션 → Link persist)
관련 축: C. DB 모델·트랜잭션·실행 계획 / unique constraint·idempotent write / 주차: 1
상태: 완료 → 확장 진행 중(2026-07-26, 토글 저장/취소 추가)
---

## 목적 (한 줄)
익스텐션에서 저장한 URL을 사용자별로 중복 없이(멱등) 최소한으로 보존한다 — 본문 수집·AI 처리는 이번 범위 밖이다.

## 범위
- **포함:** `POST /api/links`로 URL 저장, canonical URL 정규화(최소 규칙 + 추적 파라미터 제거), 사용자당 URL 유일성 보장(멱등), 초기 상태(`PENDING`) 기록. **(2026-07-26 추가)** 저장 여부 조회(`GET /api/links/lookup`), 저장 취소(`DELETE /api/links/{id}`) — 익스텐션 토글 아이콘(저장 시 색상 on, 취소 시 off, 팝업 재오픈 시 서버 조회로 상태 복원)을 위한 백엔드 API. **익스텐션 UI(아이콘 렌더링)는 이 저장소 범위 밖**이다 — 여긴 백엔드 API 계약까지만.
- **제외:** 실제 fetch·본문 추출(plan-03), 요약·임베딩·색인(plan-04), 카테고리 분류(plan-05), 목록 조회 API(plan-06), 익스텐션 프론트엔드 구현.

## 허용 쓰기 경로 (task 범위 강제용)
- `src/main/java/com/linkpocket/link/**`
- `src/main/resources/db/migration/V4__link.sql` (이번 확장은 스키마 변경 없음 — 정규화 로직·API만 추가)
- 이 경로 밖을 건드려야 한다면 계획 부족이므로 멈추고 plan을 먼저 갱신한다([development-loop.md 정지 조건](../development-loop.md)).

## Acceptance Criteria (수용 기준)
- [ ] 인증된 사용자가 유효한 `http(s)` URL을 저장하면 `200`과 `{linkId, canonicalUrl, status, alreadyExisted}`를 반환한다.
- [ ] 처음 저장하는 URL은 `alreadyExisted: false`, 이미 저장된(canonical 기준 동일) URL을 다시 저장하면 `alreadyExisted: true` + 기존과 동일한 `linkId`를 반환한다.
- [ ] 같은 사용자가 같은 canonical URL을 동시에 100회 저장해도 row는 1개만 생성되고, 모든 요청이 같은 `linkId`를 반환한다.
- [ ] `http`/`https`가 아닌 scheme(`javascript:`, `file:` 등)으로 저장을 시도하면 `400 LINK_INVALID_URL`을 반환하고 저장하지 않는다.
- [ ] 인증되지 않은 요청은 기존 `AUTH_SESSION_INVALID`(401, ADR-006)로 거부된다 — 이 도메인에서 새로 정의하지 않는다.
- [ ] 서로 다른 두 사용자가 완전히 같은 URL을 각자 저장하면 각자의 row가 독립적으로 생성된다(사용자 간 유일성 제약이 아니라 사용자별 유일성 제약).
- [ ] `utm_source`/`utm_medium`/`utm_campaign`/`utm_term`/`utm_content`/`gclid`/`fbclid`/`msclkid`/`mc_eid` 파라미터만 다르고 나머지가 같은 URL은 같은 `canonicalUrl`로 취급된다(같은 `linkId`).
- [ ] 위 목록에 없는 다른 쿼리 파라미터가 다르면 별개의 URL로 취급된다(canonical이 달라짐) — 정규화가 과하게 넓어지지 않았음을 보장.
- [ ] **(신규)** `GET /api/links/lookup?url=...`로 저장 여부를 조회하면, 저장돼 있으면 `{saved: true, linkId}`, 아니면 `{saved: false, linkId: null}`을 반환한다.
- [ ] **(신규)** `DELETE /api/links/{linkId}`로 본인 소유 링크를 삭제하면 `204`를 반환하고 row가 실제로 사라진다(하드 삭제).
- [ ] **(신규)** 삭제 후 같은 URL을 다시 저장하면 완전히 새로운 저장으로 취급된다(`alreadyExisted: false`, 새 `linkId`).
- [ ] **(신규)** 다른 사용자 소유이거나 존재하지 않는 `linkId`를 삭제 시도하면 `404 LINK_NOT_FOUND`를 반환한다(권한 없음과 존재 안 함을 구분하지 않음).

## 불변식 (항상 참이어야 하는 것)
- 같은 `user_id` + `canonical_url` 조합은 항상 row가 1개다(멱등, [ADR-010](../decisions/adr-010-link-idempotent-save.md) 결정 1).
- 클라이언트가 보낸 원본 `url`은 그대로 보존하고, 중복 판정에는 오직 `canonical_url`만 사용한다.
- Link 저장 트랜잭션 안에 외부 HTTP 호출이 없다(전부 로컬 DB 작업으로 완결).
- 서버는 세션의 `userId`만 신뢰한다 — 요청 바디의 `userId`류 파라미터는 애초에 받지 않는다(ADR-006 결정 4 재사용).

## 실패 조건 (이렇게 되면 실패로 본다)
- 같은 canonical URL 동시 100회 저장에서 row가 2개 이상 생기면 실패.
- `http`/`https`가 아닌 scheme에 `400`을 반환하지 않으면 실패.
- 사용자 A의 canonical URL이 이미 존재한다는 이유로 사용자 B의 동일 URL 저장이 막히거나 A의 `linkId`를 돌려주면 실패(tenant 경계 위반).
- `canonical_url` 계산이 요청마다 달라져(비결정적) 같은 URL인데 다른 row가 생기면 실패.

## 위험 로직 결정 (합의 완료 — [ADR-010](../decisions/adr-010-link-idempotent-save.md))
- 동시 중복 저장 방지: `(user_id, canonical_url)` unique constraint + `INSERT ... ON CONFLICT` 원자적 upsert → ADR-010 결정 1
- canonical URL 정규화 범위: 최소 규칙(scheme·trailing slash·`www.`) + 알려진 추적 파라미터 제거(2026-07-26 확장) → ADR-010 결정 2
- 저장 취소·조회: 하드 삭제 + 서버 조회 기반 상태 복원(로컬 저장 없음) → ADR-010 결정 3(2026-07-26 신규)

## 에러 코드 계약 (LINK 도메인)

스펙: [architecture/api-error-contract.md](../architecture/api-error-contract.md) ([ADR-007](../decisions/adr-007-domain-error-code-contract.md)). `LinkErrorCode` enum을 아래 표로 먼저 정의한 뒤 API를 구현한다.

| 코드 | HTTP status | 화면 처리 | 사용자 문구 owner |
|---|---|---|---|
| `LINK_INVALID_URL` | 400 | 인라인 필드 에러("올바른 URL이 아닙니다") | BE 기본값 |
| `LINK_NOT_FOUND` | 404 | 조용히 실패(토글 아이콘은 이미 꺼진 것으로 취급) — 존재 여부를 숨김([architecture/api-error-contract.md](../architecture/api-error-contract.md) IDOR 원칙) | FE 재정의 |

이 도메인은 인증 실패를 자체 정의하지 않는다 — `AUTH_SESSION_INVALID`(AUTH 도메인, ADR-006)를 그대로 사용한다.

## API 계약 (초안 — 상세는 architecture/openapi로 확정)

| 메서드/경로 | 설명 | 응답 |
|---|---|---|
| `POST /api/links` | URL 저장(멱등) | `200 {linkId, canonicalUrl, status, alreadyExisted}` / `400 LINK_INVALID_URL` / `401 AUTH_SESSION_INVALID` |
| `GET /api/links/lookup?url=...` | 저장 여부 조회 | `200 {saved, linkId}` / `401 AUTH_SESSION_INVALID` |
| `DELETE /api/links/{linkId}` | 저장 취소(하드 삭제) | `204` / `404 LINK_NOT_FOUND` / `401 AUTH_SESSION_INVALID` |

## 구현 계획 (AI가 따를 단계)
1. **실패 테스트 먼저** — 동시 100회 저장 시 row 1개(멱등), 다른 사용자 간 독립성, 잘못된 scheme 거부, 인증 없는 요청 거부.
2. Flyway `V4__link.sql` — `link(id uuid pk, user_id uuid references app_user(id), url text, canonical_url text, status text, created_at timestamptz)` + `unique(user_id, canonical_url)`.
3. canonical URL 정규화 순수 함수(scheme 소문자화, trailing slash 제거, `www.` 제거) — 외부 의존 없이 단위 테스트 가능하게 분리.
4. `LinkErrorCode`(`LINK_` 접두사) — scheme 검증 실패 시 사용.
5. `LinkService` — `INSERT ... ON CONFLICT (user_id, canonical_url) DO UPDATE SET url = link.url RETURNING *` 패턴(no-op update로 항상 row를 돌려받아 신규/기존 분기 없이 동일 코드 경로 유지) — 정확한 SQL 형태는 Codex 구현 시 QueryDSL/native query 중 더 적합한 쪽으로 판단.
6. `POST /api/links` 컨트롤러 — 세션에서 `userId` 추출(`AuthenticatedUserService` 패턴 재사용).

**(2026-07-26 확장, task 02b)**
7. canonical URL 정규화 함수에 추적 파라미터 제거 단계 추가(고정 목록, ADR-010 결정 2) — 목록에 없는 파라미터는 순서·값 그대로 보존.
8. `INSERT ... RETURNING ... (xmax = 0) AS inserted`로 `alreadyExisted` 판정 — `LinkSaveResponse`에 필드 추가.
9. `GET /api/links/lookup?url=...` — 같은 정규화 함수로 canonical 계산 후 `(user_id, canonical_url)` 존재 여부만 조회(부작용 없음).
10. `DELETE /api/links/{linkId}` — `user_id` 일치 확인 후 하드 삭제, 없거나 소유자 아니면 `LINK_NOT_FOUND`.

<AI가 제안한 계획이다 — 사람이 대조·수정한다.>

## 대조 기록 (SDD 증거)
- AI 계획 중 수정/거절한 것과 이유:
- spec↔구현 누락 건수 · 수정 turn 수:
- 동시 100회 저장 검증 결과:
