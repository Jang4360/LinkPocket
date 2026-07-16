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

> 개발 루프 전체(계약 우선·2에이전트·사람 게이트)는 [development-loop.md](../development-loop.md)에, 그 근거 결정들은 위 3번 범주 ADR에 있다.

## 새 ADR 추가 규칙
- 파일명 `adr-NNN-제목.md`, 번호는 범주와 무관하게 순차. 범주는 헤더 `범주:`로.
- 아직 안 쓴 핵심 ADR 후보(기술·아키텍처): retrieval 전략(dense·BM25·hybrid) / cache 정책 / MCP·권한 경계 / OpenTelemetry backend.

## ADR 템플릿

```markdown
# ADR-NNN: 제목

- 날짜: YYYY-MM-DD / 상태: 제안 | 확정 | 대체됨(→ ADR-MMM)
- 범주: 기술 | 아키텍처 | 프로세스

## 상황 (어떤 문제·신호가 있었나)
## 선택지 (대안과 각각의 장단점)
## 결정 (무엇을, 결정적 이유)
## 트레이드오프 (감수한 것)
## 재검토 조건 (언제 이 결정을 다시 여는가)
```

## ADR 템플릿

```markdown
# ADR-NNN: 제목

- 날짜: YYYY-MM-DD / 상태: 제안 | 확정 | 대체됨(→ ADR-MMM)

## 상황 (어떤 문제·신호가 있었나)
## 선택지 (대안과 각각의 장단점)
## 결정 (무엇을, 결정적 이유)
## 트레이드오프 (감수한 것)
## 재검토 조건 (언제 이 결정을 다시 여는가)
```

핵심 ADR 5~7개 목표 (아직 안 쓴 것):
retrieval 전략(dense·BM25·hybrid) / cache 정책(비용용 vs 성능용) / MCP·권한 경계 / OpenTelemetry backend 선택.
(modular monolith·비동기 진화는 위 두 파일로 이미 작성됨)
