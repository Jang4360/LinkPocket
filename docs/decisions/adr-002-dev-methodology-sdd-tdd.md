# ADR-002: 개발 방법론 — 큰 그림은 SDD, 구현은 TDD

- 날짜: 2026-07-15 / 상태: 확정
- **범주: 개발 프로세스 (방법론)**
- 관련: [development-loop.md](../development-loop.md), [plan/README.md](../plan/README.md)

> 철학 정합: 계약(SDD)·테스트(TDD)가 **하네스의 채점기이자 루프의 고정 타깃**이다. loop/harness engineering 근거는 [development-loop.md 철학적 근거](../development-loop.md) 참고.

## 상황

AI 에이전트에게 "만들어줘"만 주면 구현과 테스트를 동시에 지어내고 초록불만 맞춘다 (계약 없는 통과). "무엇을 만들지(WHAT)"와 "어떻게 만들지(HOW)"를 서로 다른 고도에서 분리해야 회귀·환각을 막을 수 있다.

## 선택지

| 옵션 | 장점 | 단점 |
|---|---|---|
| 자유 위임 (계약 없이 AI에) | 빠름 | 계약 부재 → 회귀·환각이 새어나감 |
| TDD만 | 코드 수준 안전망 | 무엇을 만들지에 대한 상위 계약이 없음 |
| SDD만 | 상위 계약 명확 | 구현 수준 회귀 방어가 약함 |
| **SDD(바깥) + TDD(안) 중첩** | WHAT·HOW를 각 고도에서 고정 | 계약·테스트 선작성 비용 |

## 결정

**바깥 루프 = SDD, 안쪽 루프 = TDD. 둘은 경쟁이 아니라 중첩된다.**

| 고도 | 누가 | 무엇 | 방법론 |
|---|---|---|---|
| 계약 (spec 수준) | Claude | 계약 테스트(빨강) — spec을 실행 가능한 형태로 | **SDD** |
| 구현 (코드 수준) | Codex | 내부 단위 테스트 red-green-refactor | **TDD** |

- SDD: `plan/` 문서(acceptance criteria·불변식·실패 조건)가 계약이자 진실의 원천.
- TDD: 그 계약을 만족시키는 task를 구현할 때 실패 테스트 → 최소 구현 → 리팩터.
- "테스트 먼저"가 두 곳에서 일어난다 — 계약 테스트(Claude, SDD)와 내부 단위 테스트(Codex, TDD).

## 트레이드오프

- 계약·테스트를 먼저 쓰는 초기 비용이 있다. → 소기능(one-sentence diff)은 계약을 생략하고 직접 구현 (Anthropic도 "plan 생략" 권장).
- AI가 자유롭게 못 짠다. → 그게 목적이다. 회귀·환각 차단이 자유보다 우선.

## 재검토 조건

- SDD 오버헤드가 사소한 변경까지 잠식하면, 위험 로직·핵심 API에만 계약을 적용하고 나머지는 경량화한다.

## 근거 (외부)

- GitHub Spec Kit — 스펙을 "living, executable artifact"로 (SDD).
- 카카오페이 SDD — 개발자 역할이 "코드 작성자 → 스펙 검증·테스트 설계자"로 이동.
- Anthropic Claude Code — "one Claude writes tests, then another writes code to pass them."
- 링크: [reference/sources.md](../reference/sources.md)

## 면접 답변 요지

> "AI가 구현과 테스트를 동시에 지어내는 걸 막으려고, 무엇을 만들지는 SDD 계약으로 사람·Claude가 먼저 못박고, 어떻게 만들지는 Codex가 TDD 안쪽 루프로 채우게 분리했다."
