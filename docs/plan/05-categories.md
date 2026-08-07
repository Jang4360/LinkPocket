---
기능: 카테고리 CRUD·다중 분류·삭제 전파·사용자 수정 보존
관련 축: C(데이터 모델·트랜잭션) / 주차: 3~4
상태: 초안
---

## 목적 (한 줄)
사용자가 링크를 카테고리로 직접 정리할 수 있게 하되, 카테고리를 지워도 링크는 절대 사라지지 않고, 사용자가 직접 고친 제목·요약은 AI가 다시 덮어쓰지 않는다.

## 범위 — 스코프 판단(사람 확인 필요)
- **포함:** Category CRUD, Link-Category 다중 분류(N:M), 카테고리 삭제 시 연결 해제(링크 보존)+"카테고리 없음" 자동 재분류, `title_source`/`summary_source`로 사용자 수정 보존, 기존 plan-04 요약 job이 이 source를 존중하도록 수정.
- **제외(스코프 판단, 사람 확인 필요):** 로드맵 설명의 "제목/요약/분류 보정"이 가리키는 **AI가 능동적으로 title/summary를 재요약하거나 카테고리를 제안하는 별도 job**은 이 plan에 없다. 그런 job이 존재해야 "보정"이 실제로 일어나는데, 지금은 그 job 자체가 설계된 적이 없다(plan-04는 최초 1회 요약만 한다). 이 plan은 **"만약 미래에 그런 job이 생기면 사용자 수정을 절대 덮어쓰지 못하게 막는 안전장치"**(source 컬럼 + 원자적 조건부 UPDATE)까지만 만든다. 재색인(카테고리·제목 변경 후 임베딩 갱신)도 plan-06(검색) 인프라가 있어야 의미가 있어 이 plan에서 제외한다.

## Acceptance Criteria (수용 기준)
- [ ] 사용자가 카테고리 관련 기능을 처음 쓰면 "카테고리 없음" 시스템 카테고리가 자동 생성돼 있다(idempotent, 매번 새로 안 만들어짐).
- [ ] 새 Link는 저장 시 기본으로 "카테고리 없음"에 연결된다.
- [ ] 사용자가 Link에 실제 카테고리를 하나 이상 지정하면 "카테고리 없음" 연결은 자동으로 해제된다.
- [ ] Link의 마지막 실제 카테고리 연결이 없어지면(수동 해제, 또는 카테고리 삭제로 인한 연쇄 해제) 자동으로 "카테고리 없음"에 다시 연결된다.
- [ ] Link 하나가 "카테고리 없음"과 실제 카테고리를 동시에 갖는 상태는 어떤 시점에도 없다.
- [ ] "카테고리 없음"은 사용자가 삭제·이름변경 할 수 없다(시도 시 `CATEGORY_SYSTEM_PROTECTED`).
- [ ] 카테고리를 삭제해도 그 카테고리에 연결됐던 Link row는 삭제되지 않고 그대로 남는다.
- [ ] Link 하나는 실제 카테고리 여러 개에 동시에 속할 수 있다(다중 분류).
- [ ] 사용자가 title 또는 summary를 직접 수정하면 해당 `*_source`가 `USER_EDITED`로 전환된다.
- [ ] `*_source`가 `USER_EDITED`인 필드는 그 이후 어떤 AI 쓰기 시도도 덮어쓰지 못한다(동시 실행 포함).
- [ ] 남의 카테고리를 조회·수정·삭제·연결 시도하면 `CATEGORY_NOT_FOUND`(404, IDOR 통일)로 응답한다.

## 불변식 (항상 참이어야 하는 것)
- Link는 항상 "카테고리 없음" **또는** 실제 카테고리 1개 이상 중 정확히 하나의 상태다(상호 배타, 무소속 상태는 없음).
- 사용자별 "카테고리 없음" row는 정확히 1개다.
- 카테고리 삭제가 Link row 삭제로 이어지지 않는다([invariants.md](../product/invariants.md) 전역 불변조건 재확인).
- `title_source`/`summary_source`가 `USER_EDITED`인 필드는 AI 프로세스가 절대 쓰지 않는다 — 원자적 조건부 UPDATE로 보장([ADR-015](../decisions/adr-015-category-deletion-and-correction-overwrite.md) 결정 2).
- 서버가 세션에서 뽑은 `userId` 소유의 Category만 이 기능이 조회·조작한다(tenant 격리).

## 실패 조건 (이렇게 되면 실패로 본다)
- 카테고리 삭제로 연결된 Link가 함께 삭제되면 실패.
- 어떤 시점에든 Link가 "카테고리 없음"+실제 카테고리를 동시에 가지면 실패.
- "카테고리 없음"이 삭제되거나 사용자당 2개 이상 존재하면 실패.
- 사용자가 title/summary를 수정한 뒤 AI 쓰기 시도(동시 실행 포함)가 그 값을 덮어쓰면 실패.
- 남의 카테고리에 대한 조작이 403이 아니라 정보를 흘리는 다른 응답으로 처리되면 실패(IDOR).

## 위험 로직 결정 (합의 완료 — [ADR-015](../decisions/adr-015-category-deletion-and-correction-overwrite.md))
- 카테고리 삭제 전파: 연결(`link_category`)만 `ON DELETE CASCADE`, Link는 보존, "카테고리 없음"으로 자동 재분류 → ADR-015 결정 1
- AI 보정의 사용자 수정 보존: `title_source`/`summary_source` + 원자적 조건부 UPDATE(plan-01/03/04 재사용 패턴) → ADR-015 결정 2

## 에러 코드 계약

| 코드 | HTTP status | 화면 처리 | 사용자 문구 owner |
|---|---|---|---|
| `CATEGORY_NOT_FOUND` | 404 | 토스트 | BE 기본값 |
| `CATEGORY_SYSTEM_PROTECTED` | 400 | 인라인 필드 에러("카테고리 없음"은 수정·삭제 불가) | BE 기본값 |
| `CATEGORY_DUPLICATE_NAME` | 409 | 인라인 필드 에러 | BE 기본값 |

## API 계약

| 메서드/경로 | 설명 |
|---|---|
| `POST /api/categories` `{name}` | 카테고리 생성. 소유자=세션 userId |
| `GET /api/categories` | 사용자 카테고리 목록("카테고리 없음" 포함, `isSystem` 플래그) |
| `PATCH /api/categories/{id}` `{name}` | 이름 변경. system 카테고리는 `CATEGORY_SYSTEM_PROTECTED` |
| `DELETE /api/categories/{id}` | 삭제(연결 해제+영향받은 Link 재분류). system 카테고리는 `CATEGORY_SYSTEM_PROTECTED` |
| `PUT /api/links/{linkId}/categories` `{categoryIds: []}` | Link의 카테고리 집합을 통째로 교체(빈 배열이면 "카테고리 없음"으로 귀결) |
| `PATCH /api/links/{linkId}` `{title?, summary?}` | 사용자 수정 — 전달된 필드의 `*_source`를 `USER_EDITED`로 전환 |

## 구현 계획 (AI가 따를 단계)
1. **실패 테스트 먼저(Claude)** — 카테고리 CRUD tenant 격리, 삭제 시 링크 보존+"카테고리 없음" 재분류, 상호 배타 불변식(동시 조작 포함), system 카테고리 보호, `*_source` 원자적 no-overwrite(동시 사용자 수정 vs AI 쓰기 경쟁).
2. Flyway 마이그레이션(V8) — `category(id, user_id, name, is_system boolean default false, created_at)` `unique(user_id, name)` + `unique(user_id) where is_system` 부분 유니크 인덱스. `link_category(link_id, category_id)` PK, `category_id` FK `ON DELETE CASCADE`, `link_id` FK `ON DELETE CASCADE`. `link`에 `title_source text default 'AI_GENERATED'`, `summary_source text default 'AI_GENERATED'` 추가.
3. `CategoryService.ensureUncategorized(userId)` — idempotent get-or-create(unique 제약 + `ON CONFLICT DO NOTHING` 후 재조회).
4. `LinkCategoryService` — 카테고리 지정/해제 시 같은 트랜잭션 안에서 상호 배타 전환(실제 카테고리 추가 시 "카테고리 없음" 삭제, 마지막 실제 카테고리 제거 시 "카테고리 없음" 재삽입). 카테고리 삭제 후 영향받은 Link들 일괄 재분류도 같은 트랜잭션.
5. `LinkController`에 `PATCH /api/links/{linkId}` 추가 — title/summary 수정 시 `*_source='USER_EDITED'`로 원자적 전환.
6. 기존 `LinkProcessingWorkerImpl`(plan-04)의 요약 쓰기 UPDATE에 `AND summary_source = 'AI_GENERATED'` 조건 추가(현재는 이 조건이 없음 — 지금 당장은 사용자가 요약을 수정할 방법이 없어 문제가 드러나지 않았을 뿐, plan-05가 그 경로를 열기 전에 막아야 함).

<AI가 제안한 계획이다 — 사람이 대조·수정한다. 특히 스코프 판단(AI 능동 보정 job 제외)을 확인 필요.>

## 대조 기록 (SDD 증거)
- AI 계획 중 수정/거절한 것과 이유:
- spec↔구현 누락 건수 · 수정 turn 수:
