# decisions — 의사결정 기록

결정은 **세 범주**로 나눈다. 각 ADR 헤더의 `범주:`로 구분하고, 파일도 범주가 섞이지 않게 둔다.

## 1. 기술 선택 (Technical)
- `기술스택.md` — 0주차 확정 스택 전체 (Java21·Spring·JPA/QueryDSL·pgvector·Google OAuth·OpenAI·Next.js·WXT·REST Docs…). **ADR 0세대.** 각 항목이 대안별 택한 핵심 이유·트레이드오프·재검토 조건·면접 답변까지 담는다.
- `conditional-tech-adoption.md` — Kafka·Redis·SSE·Qdrant 보류 근거와 도입 신호 판정표, 비동기 Job polling→Outbox→Kafka 3단계 진화.

## 2. 시스템 아키텍처/구조 (Architecture)
- `adr-001-modular-monolith.md` — 구조를 MSA가 아닌 modular monolith로 (Domain = 패키지 경계).

## 3. 개발 프로세스/방법론 (Process)
- `adr-002-dev-methodology-sdd-tdd.md` — 큰 그림은 SDD, 구현은 TDD (중첩).
- `adr-003-work-decomposition-and-branching.md` — Domain ⊃ Feature ⊃ Task → Trunk(main) 브랜치·작업 분해.
- `adr-004-ai-context-files.md` — CLAUDE.md/AGENTS.md를 ≤60줄 "라우터"로.
- `adr-005-mistake-promotion.md` — 반복 실수를 skill/hook으로 승격하는 하네스 자기개선 규칙.
- `adr-006-auth-session-architecture.md` — 웹 세션(HttpOnly 쿠키)·익스텐션 PKCE·토큰 보관·tenant 경계 강제 (plan-01 근거).
- `adr-007-domain-error-code-contract.md` — 도메인별 비즈니스 에러 코드 enum + 공통 envelope + BE-FE 화면 처리 계약. 모든 plan이 따르는 횡단 규칙.
- `adr-008-harness-hardening.md` — 외부 하네스 개선 제안 검토 결과: invariants.md·STOP CONDITIONS·check-secrets.sh·CODEOWNERS 채택, R0~R4/verify 분리/deploy.yml 보류.
- `adr-009-ai-harness-architecture.md` — **하네스 전체 구조·철학 종합.** 개별 ADR(001~008)을 하나의 그림(4층: 컨텍스트→루프→4겹강제→자기개선)으로 잇는 문서. 큰 그림이 궁금하면 여기부터.
- `adr-010-link-idempotent-save.md` — Link 동시 중복 저장 방지(unique constraint + 원자적 upsert)·canonical URL 정규화 범위 (plan-02 근거).
- `adr-011-safe-fetch-ssrf-timeout-retry.md` — SSRF 방어 깊이(DNS+IP 검증, redirect 재검증)·timeout 3구간·재시도/크기 제한·중복 fetch 방지 (plan-03 근거).
- `adr-012-async-pipeline-job-claim-and-idempotency.md` — job claim 동시성(SKIP LOCKED)·트랜잭션 경계와 lease 기반 복구·멱등성(호출 전 조회+unique constraint) (plan-04 근거).
- `adr-013-chunking-strategy.md` — 본문 청킹 전략: 구조 보존 우선 + 고정 크기 fallback + overlap (plan-04 근거).
- `adr-014-ai-processing-failure-exposure-and-retry.md` — AI 처리(요약·임베딩) 영구 실패의 목록 노출 방식과 재시도 가능/불가 사유 구분·상한(최대 2회) (plan-04 근거).

> 개발 루프 전체(계약 우선·2에이전트·사람 게이트)는 [development-loop.md](../development-loop.md)에, 그 근거 결정들은 위 3번 범주 ADR에 있다.

## 새 ADR 추가 규칙
- 파일명 `adr-NNN-제목.md`, 번호는 범주와 무관하게 순차. 범주는 헤더 `범주:`로.
- 아직 안 쓴 핵심 ADR 후보(기술·아키텍처): retrieval 전략(dense·BM25·hybrid) / cache 정책 / MCP·권한 경계 / OpenTelemetry backend.

## ADR 템플릿 (2026-07-26 개정 — 6단계 구조)

**위험 로직 ADR**(plan/README.md의 "계획 전 필수" 절차를 거치는 것)은 이 구조를 쓴다. **문제 정의·선택 이유는 사람이 실제로 말한 것을 그대로 옮긴다** — Claude가 사후에 매끄럽게 재구성하지 않는다. "결과"는 구현 전엔 비워두고, 실측치가 나오면 반드시 채운다(비어있는 채로 두지 않는다).

```markdown
# ADR-NNN: 제목

- 날짜: YYYY-MM-DD / 상태: 제안 | 확정 | 대체됨(→ ADR-MMM)
- 범주: 기술 | 아키텍처 | 프로세스

## 문제 정의 (사람이 직접 정의 — 사용자 가치·품질 기준)
<이 기능에서 사용자가 실제로 기대하는 것, 우선순위를 둔 품질 기준(가용성/정확성/보안/비용 등).
Claude가 질문하고 사람이 답한 것을 그대로 옮긴다.>

## 가설
<이 문제를 이렇게 풀면 될 것이라는 가정. 아직 검증 전.>

## 대안 (선택지와 트레이드오프)
| 선택지 | 트레이드오프 |
|---|---|
| ... | ... |

## 선택 (무엇을, 사람의 판단 근거)
<사람이 실제로 고른 것과 그 이유 — 위 "문제 정의"의 기준과 연결되게.>

## 관찰 지표
<이 선택이 맞았는지 확인할 지표. 이름을 미리 정해둔다.>

## 결과 (구현·운영 후 채움)
<실측치, 발견된 격차, 후속 결정. 비워두지 않는다 — 아직 없으면 "미측정"이라고 명시한다.>

## 재검토 조건 (언제 이 결정을 다시 여는가)
```

이 구조 이전에 쓰인 ADR(001~011)은 "상황/선택지/결정/트레이드오프/재검토조건" 5단계였다 — 소급 개정하지 않고, 회고(retro) 대상이 될 때 그 김에 6단계로 다시 쓴다.

핵심 ADR 5~7개 목표 (아직 안 쓴 것):
retrieval 전략(dense·BM25·hybrid) / cache 정책(비용용 vs 성능용) / MCP·권한 경계 / OpenTelemetry backend 선택.
(modular monolith·비동기 진화는 위 두 파일로 이미 작성됨)
