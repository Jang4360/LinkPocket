# ADR-004: AI 하네스 컨텍스트 파일 — CLAUDE.md/AGENTS.md를 "라우터"로

- 날짜: 2026-07-15 / 상태: 확정
- **범주: 개발 프로세스 (AI 하네스)**
- 관련: [development-loop.md](../development-loop.md), [README.md](../README.md)

> 철학 정합: CLAUDE.md/AGENTS.md 라우터 = **하네스의 컨텍스트 계층**(progressive disclosure). loop/harness engineering 근거는 [development-loop.md 철학적 근거](../development-loop.md) 참고.

## 상황

컨텍스트 창은 빨리 차고, 차면 성능이 떨어진다. 큰 지침 파일(400줄)은 오히려 무시된다. 두 에이전트(Claude Code=계획·리뷰, Codex=구현)가 **세션 시작 시 무엇을 읽어야 하는지** 알아야 하는데, 모든 내용을 파일에 인라인하면 비대해져 규칙이 묻힌다.

## 선택지

| 옵션 | 장점 | 단점 |
|---|---|---|
| 모든 규칙 인라인 | 한 파일에 다 있음 | 비대 → 규칙이 무시됨(Anthropic 경고) |
| 컨텍스트 파일 없음 | 관리 0 | 에이전트가 매번 헤맴, 보호 경로 모름 |
| **라우터 + progressive disclosure** | 컨텍스트 절약, 필요한 것만 로드 | 라우팅 문구 유지보수 필요 |

## 결정

**CLAUDE.md·AGENTS.md를 ≤60줄의 "라우터(목차)"로 둔다.** 내용을 담지 않고 *어디를 읽을지 가리킨다*. 세 덩어리로 구성:

1. **라우팅 맵 (목차)** — "이 작업엔 이 문서" (예: 기능 구현 전 → `docs/plan/NN`, 개발 규칙 → `docs/development-loop.md`)
2. **절대 규칙** — definition of done + 보호 경로(사람만 수정: 계약 테스트·`verify.sh`·`.claude/settings.json`·CI)
3. **AI가 못 추측하는 것만** — 빌드/테스트 명령어, 프로젝트 특유 관례

규칙:
- **참조는 한 단계만.** 문서→문서→문서 체이닝 금지(에이전트가 길을 잃음).
- 상세·도메인 지식은 파일에 넣지 말고 `skill` 또는 `docs/`로 빼서 on-demand 로드.
- **AGENTS.md는 오픈 표준**(60,000+ 프로젝트, Codex가 읽음). 프로젝트 사실·라우팅은 CLAUDE.md와 공유하고 **역할만 다르게**(Codex=구현) 둔다. 중복은 `docs/` 링크로 단일화.

## 트레이드오프

- 라우팅 문구가 부정확하면 잘못된 문서를 로드한다. → 라우팅 실수가 반복되면 문구를 다듬는다(CLAUDE.md를 코드처럼 리뷰·프루닝).
- 파일이 커지면 다시 비대해진다. → 60줄 넘으면 내용을 skill로 이동.

## 재검토 조건

- 에이전트가 CLAUDE.md에 있는데도 반복해서 틀리면 파일이 너무 길다는 신호 → 프루닝하거나 hook으로 전환.
- 라우팅 항목이 많아지면 skill 기반 discovery로 이동.

## 근거 (외부)

- Anthropic Claude Code — CLAUDE.md는 ≤60줄, 안 변하는 규칙·DoD·건드리면 안 되는 것만. `@import`·skill on-demand.
- Progressive disclosure — CLAUDE.md = "목차(Map)", 필요한 것만 로드. 참조 한 단계.
- agents.md 오픈 표준 — 섹션: 개요·빌드/테스트·코드 스타일·테스트·보안·PR 규칙. 가장 가까운 파일이 우선.
- 링크: [reference/sources.md](../reference/sources.md)

## 면접 답변 요지

> "컨텍스트 창이 곧 자원이라, CLAUDE.md·AGENTS.md를 규칙 덤프가 아니라 '어떤 작업엔 어떤 문서를 읽어라'를 가리키는 60줄짜리 라우터로 설계했다. 상세는 skill·docs로 빼서 필요할 때만 로드한다."
