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
| [01-auth-google-oauth](01-auth-google-oauth.md) | 웹/익스텐션 Google OAuth(PKCE)·세션·tenant 경계 + 공통 에러 프레임워크(1회 구축) | 00 | 1 | 6 | ✔ 세션 경계·토큰 회전([ADR-006](../decisions/adr-006-auth-session-architecture.md)) · 에러 계약([ADR-007](../decisions/adr-007-domain-error-code-contract.md)) | 완료 |
| [02-link-save-minimal](02-link-save-minimal.md) | 익스텐션 저장 → Link 최소 보존 + 상태(persist만) | 01 | 1 | 3~5 | ✔✔ 멱등(user+canonical=1행)·동시 저장([ADR-010](../decisions/adr-010-link-idempotent-save.md)) | 완료 |
| [03-safe-fetch-extract](03-safe-fetch-extract.md) | SSRF-safe fetch + 본문 추출 + 상태전이(AI 없이) | 02 | 2 | 4~6 | ✔ SSRF·timeout·크기 제한([ADR-011](../decisions/adr-011-safe-fetch-ssrf-timeout-retry.md)) | 완료 |
| [04-async-ai-pipeline](04-async-ai-pipeline.md) | Job polling·요약·임베딩·pgvector 색인·상태머신·멱등 | 03 | 3 | 6~8 | ✔✔✔ job claim(SKIP LOCKED)·tx 경계·at-least-once([ADR-012](../decisions/adr-012-async-pipeline-job-claim-and-idempotency.md)), chunking([ADR-013](../decisions/adr-013-chunking-strategy.md)), AI 실패 노출·재시도([ADR-014](../decisions/adr-014-ai-processing-failure-exposure-and-retry.md)) | 완료 |
| [05-categories](05-categories.md) | 카테고리 CRUD·다중 분류·삭제 전파·사용자 수정 보존(AI 능동 보정 job은 범위 밖) | 02 | 3~4 | 4~6 | ✔ 삭제=연결해제+자동 재분류·보정 no-overwrite([ADR-015](../decisions/adr-015-category-deletion-and-correction-overwrite.md)) | 완료 |
| 06-archive-and-search | 목록·카테고리 탐색·keyset pagination·자연어 시맨틱 검색 | 04,05 | 4 | 5~7 | ✔ tenant filter 서버 강제 | 대기 |
| 07-related-links | 저장 완료 polling → 연관 추천(≥80%·7일 미열람·최대 3) | 04,06 | 5 | 3~5 | ✔ tenant·임계값 | 대기 |
| 08-open-events-redirect | redirect + `openedAt`·`openCount`·`source` | 06,07 | 5 | 2~4 | 소 | 대기 |
| 09-weekly-digest (P1) | scheduler·클러스터·이메일·snooze/영구제외 | 04,08 | 5~6/알파 후 | 6~8 | ✔✔ 스케줄러 중복·발송 멱등·at-least-once | 대기 |

- **조정 여지:** 04가 커지면 `04a-summary`/`04b-embed-index`로 분할, 08은 06에 흡수 가능.
- **선행 산출물**(설계확정안 §8: ERD·OpenAPI·상태머신)은 [architecture/](../architecture/)에 축적하며 각 plan이 참조한다.
- **알파 종료 직후 최우선 순위(신규, 2026-07-27):** AI 비용 계측(`cost/link`)·예산 정책([operations/ai-cost-and-rate-limits.md](../operations/ai-cost-and-rate-limits.md) §1) 실제 구현 — 정책 문서는 이미 있으나 아직 plan 번호 미배정. 09-weekly-digest(P1)보다 먼저 진행한다. 착수 시 plan-NN으로 새로 계약화할 것(이 로드맵 표에 추가).
- **`02c-offline-save-queue`(신규, 2026-07-27, 보류):** 확장 프로그램에 IndexedDB 기반 저장 명령 대기열(오프라인 저장 → 재연결 재시도 → `clientRequestId`+canonical URL로 서버 멱등 저장). plan-01b/01c와 같은 관례로 완료된 plan-02에 붙는 후속 task다. **알파 이전에 착수하지 않는다** — 이 프로젝트의 반복 원칙(HNSW·Redis·Kafka 모두 "지표 확인 후 도입")과 같은 이유로, `alpha-analytics-contract.md`의 `link_saved` 실패 이벤트로 네트워크 원인 저장 실패가 실제로 관측된 뒤 우선순위를 정한다. 09-digest와 같은 "실측 신호 대기" 카테고리.
- **실험 스프린트(신규, 2026-07-27) — plan-05와 plan-06 사이에 낀다:**
  - `exp-06`(SSRF 안전성 회귀)·`exp-03`(장애 주입·복구)은 plan-03/04만 있으면 되고 인프라 의존이 없어 **지금(plan-05와 병행) 바로 시작**한다.
  - `exp-01`(DB pool)·`exp-02`(worker capacity)·`exp-05`(AI latency·비용 메커닉)는 실제 OCI 배포가 선행조건이라 **plan-05가 머지된 직후, plan-06(검색) 착수 전**에 진행한다: OCI에 1인스턴스로 실험용 최소 배포 → 이 배포에서 exp-01→02→05 순서로 실행(이 1인스턴스가 동시에 `exp-07`의 baseline이 된다, 배포를 두 번 안 해도 됨) → worker profile 조건부 설정(기술스택.md 2-15 "미해결" 항목) 구현 후 2인스턴스로 전환 → `exp-07`(인스턴스 격리) after 측정.
  - 알파 배포 위상(1 vs 2인스턴스)은 exp-07 결과로 최종 확정한다. plan-06(검색) 착수는 이 실험 스프린트가 끝난 뒤.

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

**절차 (2026-07-26 개정 — 순서가 핵심이다):**
1. Claude가 이 기능의 위험 지점을 **식별**한다.
2. **Claude가 먼저 질문한다 — 아직 선택지를 제시하지 않는다.** 이 기능에서 사용자가 실제로 기대하는 게 뭔지, 품질 기준(가용성·정확성·보안·비용·지연시간 등) 중 뭘 우선하는지를 묻는다. **여기서 대안·추천을 함께 주면 안 된다** — 사람이 자기 기준으로 먼저 생각하게 둔다.
3. 그 답을 듣고 나서야 **대안 + 각각의 트레이드오프**를 정리해 다시 제시한다. Claude의 추천은 덧붙이되, 사람이 2번 답변으로 이미 기준을 세운 뒤이므로 추천이 그 판단을 대체하지 않는다.
4. 사람과 **논의로 하나를 고른다.**
5. 그 결정을 **[decisions/](../decisions/) ADR**로 남긴다 — 6단계 구조(문제 정의→가설→대안→선택→관찰 지표→결과, 템플릿은 [decisions/README.md](../decisions/README.md)). **문제 정의·선택 이유는 사람의 말 그대로 기록한다** — Claude가 사후에 그럴듯하게 재구성하지 않는다.
6. 그 위에서 **plan 문서**를 작성한다. 아래 템플릿의 "위험 로직 결정" 칸에 그 ADR을 링크한다.
7. 구현·운영 후 실측치가 나오면 ADR의 "결과" 절에 채운다 — 이게 비어있는 ADR은 미완성으로 본다.

> Claude는 위험 지점에서 **먼저 묻고(선택지 없이), 합의된 것만 계약(계약 테스트)으로 못박는다.** 여기서 사람이 세운 기준과 고른 선택이 계약 테스트의 근거가 된다. 이 순서를 어기고 선택지부터 제시하면 사람의 판단이 아니라 Claude의 판단에 대한 승인이 돼버린다 — 이게 이 절차가 막으려는 것이다.

## 학습 아티클 — 결정 전에 개념·선택지를 정리한다

위 절차의 "선택지 + 트레이드오프 + Claude의 추천을 제시"하는 자리를, 채팅으로 흘려보내는 대신 [learning/articles/](../learning/articles/README.md)에 문서로 남긴다. 트리거는 두 갈래다.

- **위험 로직 자체(핵심)** — 이번 plan에 걸린 위험 로직 트리거(동시성·멱등성·트랜잭션 경계 등)마다 아티클을 쓴다. "정답이 unique constraint 하나뿐"처럼 보여도, **왜 다른 방법(낙관적 락·비관적 락·advisory lock 등)은 이 문제에 안 맞는지 설명하는 것 자체가 목적**이다. 이런 아티클은 plan을 넘나들며 재사용한다(예: "멱등성·동시성 제어 메커니즘" 하나가 plan-01·02·03에 전부 적용).
- **기술/라이브러리 선택(부가)** — 처음 도입하는 기술/라이브러리가 있거나, cs-learning의 "선택지가 있다면" 항목이 강하게 걸릴 때.

자세한 트리거·템플릿은 [learning/articles/README.md](../learning/articles/README.md) 참고.

**시점:** 위 절차의 1(위험 지점 식별) 다음, 2(선택지 제시)의 **자리에** 쓴다 — 채팅으로 흘려보내는 대신 문서로 남겨 사람이 자기 속도로 검토하게 한다. 아티클은 **결정 전 비교 자료**일 뿐, **최종 결정은 항상 ADR에만** 남긴다(아티클에 결정 문구를 다시 적지 않는다 — 같은 내용이 두 곳에 있으면 나중에 어긋난다).

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

## 허용 쓰기 경로 (task 범위 강제용)
- <예: src/main/java/com/linkpocket/auth/**, src/main/resources/db/migration/V2__*.sql>
- 이 경로 밖을 건드려야 한다면 계획 부족이므로 멈추고 plan을 먼저 갱신한다([development-loop.md 정지 조건](../development-loop.md)).

## Acceptance Criteria (수용 기준)
- [ ] <관찰 가능한 행동으로. 예: 저장 성공 시 status=PROCESSING과 linkId를 반환한다.>

## 불변식 (항상 참이어야 하는 것)
- <예: 같은 사용자 + canonical URL은 row가 항상 1개 (멱등).>

## 실패 조건 (이렇게 되면 실패로 본다)
- <예: 같은 URL 동시 100회 저장에서 row가 2개 이상이면 실패.>

## 위험 로직 결정 (동시성·트랜잭션 경계 등 — 합의 후 채움)
- <위험 지점>: <사람과 합의해 고른 선택> → ADR: <decisions/adr-NNN-제목.md>

## 에러 코드 계약 (도메인 — 실제 API 구현보다 먼저 확정)
스펙: [architecture/api-error-contract.md](../architecture/api-error-contract.md) ([ADR-007](../decisions/adr-007-domain-error-code-contract.md)). 이 도메인의 `{DOMAIN}ErrorCode` enum 상수를 아래 표로 먼저 정의한 뒤 API를 구현한다.

| 코드 | HTTP status | 화면 처리 | 사용자 문구 owner |
|---|---|---|---|
| `{DOMAIN}_{REASON}` | 4xx/5xx | 토스트 \| 인라인 필드 에러 \| 전체 화면 리다이렉트 \| 조용히 재시도 | BE 기본값 \| FE 재정의 |

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
