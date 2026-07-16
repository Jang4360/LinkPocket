# plan — 기능 명세 + 구현 계획 (SDD · 개발 전)

**개발 "전" 단계.** 기능 하나를 코드로 옮기기 전에, *AI가 무엇을 만족시켜야 하는지 사람이 먼저 못박는 곳*이다.

Spec-Driven Development(SDD): 카카오페이 spec-kit·토스 harness 사례처럼, AI 에이전트는 자유 서술이 아니라 여기 적힌 **acceptance criteria·불변식·실패 조건**을 계약으로 삼아 구현한다. ([reference/sources.md](../reference/sources.md))

## 왜 이 폴더가 "개발 전"인가

- AI에게 "만들어줘"만 주면 구현과 테스트를 동시에 지어내고 초록불만 맞춘다 (cs-learning B섹션·AI 한계 6종의 회귀).
- 사람이 먼저 **계약**(무엇을 만족해야 하는가)과 **대표 실패 조건**을 적고, AI는 그 계약을 채우게 한다.
- 이 문서가 곧 리뷰·테스트·수용 기준의 기준선이 된다. cs-learning B섹션의 `검증·기록할 증거`(spec↔구현 누락 건수, 수정 turn 수)가 여기서 나온다.

파일명: `NN-기능.md` — 주차·구현 순서 반영.

## 기능 분해 로드맵 (plan 00~09)

**단위:** plan 1개 = Feature 1개 = 독립 머지 가능한 수직 슬라이스(자기 계약 테스트 보유), 안에서 3~8개 task(작은 PR)로 분해. **walking-skeleton 먼저 → 넓힌다.** (근거: [설계확정안](../product/설계확정안.md), [adr-003](../decisions/adr-003-work-decomposition-and-branching.md))

| plan | 무엇 | 의존 | 주차 | task | 위험 로직(인터뷰→ADR) | 상태 |
|---|---|---|---|---|---|---|
| [00-walking-skeleton](00-walking-skeleton.md) | Gradle·Spring·Neon·Flyway·`/health` + Testcontainers 통합테스트가 CI green | — | 0 | 2~3 | 없음 | 진행 |
| [01-auth-google-oauth](01-auth-google-oauth.md) | 웹/익스텐션 Google OAuth(PKCE)·세션·tenant 경계 | 00 | 1 | 4~6 | ✔ 세션 경계·토큰 회전([ADR-006](../decisions/adr-006-auth-session-architecture.md)) | 승인 대기 |
| 02-link-save-minimal | 익스텐션 저장 → Link 최소 보존 + 상태(persist만) | 01 | 1 | 3~5 | ✔✔ 멱등(user+canonical=1행)·동시 저장 | 대기 |
| 03-safe-fetch-extract | SSRF-safe fetch + 본문 추출 + 상태전이(AI 없이) | 02 | 2 | 4~6 | ✔ SSRF·timeout·크기 제한 | 대기 |
| 04-async-ai-pipeline | Job polling·요약·임베딩·pgvector 색인·상태머신·멱등 | 03 | 3 | 6~8 | ✔✔✔ job claim(SKIP LOCKED)·tx 경계·at-least-once | 대기 |
| 05-categories | 카테고리 CRUD·다중 분류·제목/요약/분류 보정(+재색인) | 02 | 3~4 | 4~6 | ✔ 삭제=연결해제·보정 no-overwrite | 대기 |
| 06-archive-and-search | 목록·카테고리 탐색·keyset pagination·자연어 시맨틱 검색 | 04,05 | 4 | 5~7 | ✔ tenant filter 서버 강제 | 대기 |
| 07-related-links | 저장 완료 polling → 연관 추천(≥80%·7일 미열람·최대 3) | 04,06 | 5 | 3~5 | ✔ tenant·임계값 | 대기 |
| 08-open-events-redirect | redirect + `openedAt`·`openCount`·`source` | 06,07 | 5 | 2~4 | 소 | 대기 |
| 09-weekly-digest (P1) | scheduler·클러스터·이메일·snooze/영구제외 | 04,08 | 5~6/알파 후 | 6~8 | ✔✔ 스케줄러 중복·발송 멱등·at-least-once | 대기 |

- **조정 여지:** 04가 커지면 `04a-summary`/`04b-embed-index`로 분할, 08은 06에 흡수 가능.
- **선행 산출물**(설계확정안 §8: ERD·OpenAPI·상태머신)은 [architecture/](../architecture/)에 축적하며 각 plan이 참조한다.

## 계획 전 필수 — 위험 로직은 사람과 먼저 합의한다

동시성·트랜잭션 경계처럼 **잘못된 기본값이 나중에 subtle 버그가 되는 로직**은 Claude가 임의로 결정하지 않는다. plan을 쓰기 전에 이 절차를 지킨다. ([개발 루프](../development-loop.md)의 계획 단계 규칙)

**논의 트리거 — 하나라도 걸리면 먼저 질문한다:**
- **동시성 / race condition** — 동시 저장, 중복 처리, worker 경쟁(job claim)
- **트랜잭션 경계** — 무엇을 한 트랜잭션에 두고 무엇을 밖으로 뺄지, 외부 HTTP/LLM 호출 위치, 격리 수준
- **멱등성 / 재시도 시맨틱** — at-least-once에서 중복 실행 결과를 어떻게 고정할지
- **순서 보장 / 이벤트 순서**
- **저장소 간 정합성** — DB ↔ pgvector ↔ cache 삭제·갱신 전파
- **권한·tenant 경계** — 서버가 tenant를 강제하는 지점
- **비가역 작업** — 삭제·파기 등 되돌릴 수 없는 동작

**절차:**
1. Claude가 이 기능의 위험 지점을 **식별**한다.
2. 각 지점마다 **선택지 + 각 트레이드오프 + Claude의 추천**을 제시하고 **사람에게 의견을 묻는다.** (임의 결정 금지 — no silent default)
3. 사람과 **논의로 하나를 고른다.**
4. 그 결정을 **[decisions/](../decisions/) ADR**로 남긴다 (상황→선택지→결정→트레이드오프→재검토 조건).
5. 그 위에서 **plan 문서**를 작성한다. 아래 템플릿의 "위험 로직 결정" 칸에 그 ADR을 링크한다.

> Claude는 위험 지점에서 **먼저 묻고, 합의된 것만 계약(계약 테스트)으로 못박는다.** 여기서 사람이 고른 선택이 계약 테스트의 근거가 된다.

## 템플릿 (복사해서 채운다)

```markdown
---
기능: <이름>
관련 축: <cs-learning A~H>  / 주차: <N>
상태: 초안 | 승인 | 구현중 | 완료
---

## 목적 (한 줄)
<이 기능이 해결하는 사용자 문제.>

## 범위
- 포함: <이번에 만드는 것>
- 제외: <이번엔 안 하는 것 — 스코프 방어>

## Acceptance Criteria (수용 기준)
- [ ] <관찰 가능한 행동으로. 예: 저장 성공 시 status=PROCESSING과 linkId를 반환한다.>

## 불변식 (항상 참이어야 하는 것)
- <예: 같은 사용자 + canonical URL은 row가 항상 1개 (멱등).>

## 실패 조건 (이렇게 되면 실패로 본다)
- <예: 같은 URL 동시 100회 저장에서 row가 2개 이상이면 실패.>

## 위험 로직 결정 (동시성·트랜잭션 경계 등 — 합의 후 채움)
- <위험 지점>: <사람과 합의해 고른 선택> → ADR: <decisions/adr-NNN-제목.md>

## API 계약 (해당 시)
<엔드포인트·요청/응답·오류 코드·상태 enum. 확정 스펙은 architecture/openapi로.>

## 구현 계획 (AI가 따를 단계)
1. 실패 테스트 먼저 — <무엇을>
2. <다음 단계>
<AI가 제안한 계획을 여기 붙이고 사람이 대조·수정한다.>

## 대조 기록 (SDD 증거)
- AI 계획 중 수정/거절한 것과 이유:
- spec↔구현 누락 건수 · 수정 turn 수:
```

> 이 폴더는 "무엇을·왜"(product·decisions)와 "시스템 설계도"(architecture)와 다르다.
> 여기는 **기능 하나 단위의, 코딩 직전 계약**이다. 구현이 끝나면 상태를 `완료`로 두고 다음 기능으로 넘어간다.
