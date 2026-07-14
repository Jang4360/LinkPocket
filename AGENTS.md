# AGENTS.md — Codex 컨텍스트

너는 이 프로젝트에서 **구현**을 맡는다. 계획·계약 테스트·리뷰는 Claude와 사람이 한다.
먼저 읽어라: `docs/development-loop.md`(개발 규칙), 그리고 지금 구현할 `docs/plan/NN-기능.md`(계약).

## 작업 방식 (working agreement)
- `docs/plan/NN`의 acceptance criteria·불변식·실패 조건을 **계약**으로 삼아 구현한다.
- 계약 테스트(`src/test/**/contract/**`)는 **네가 짜지도, 수정·삭제하지도 않는다.** 그 테스트를 통과시키는 코드만 쓴다.
- 내부 단위 테스트는 추가해도 된다(계약을 강화만). red-green-refactor로.
- 변경 전·후 반드시 `./scripts/verify.sh`를 실행해 green을 확인한다. (Codex는 명령을 자동 실행하지 않으니 **명시적으로 돌려라**.)

## 하면 안 되는 것 (보호 경로 — 사람만)
- `src/test/**/contract/**`, `scripts/verify.sh`, `scripts/check-protected.sh`, `.claude/**`, `.codex/**`, `.github/workflows/**`
- 게이트가 막히면 **우회 경로를 찾지 말고** 멈춰서 사람에게 보고한다. 테스트를 무르게 고쳐 통과시키지 않는다.
- 계획(plan) 밖 파일을 건드리지 않는다. diff는 해당 task 범위 안에 둔다.

## 강제 (네게 걸린 훅)
- `.codex/hooks.json`이 위 보호 경로 편집을 차단(PreToolUse)하고, 턴 종료 시 `verify.sh`를 강제(Stop)한다.
- 최초 1회 `/hooks`로 이 훅을 **신뢰(trust)** 해야 활성화된다. 훅 정의가 바뀌면 재승인이 필요하다.

## 통합
- 작은 단위(task)로 커밋/PR. Trunk(main)에 자주, 미완성은 feature flag OFF. (근거: `docs/decisions/adr-003-work-decomposition-and-branching.md`)

## 스택
- Java 21 + Spring Boot + JPA/QueryDSL, Gradle Kotlin DSL.
- 테스트: JUnit 5 · AssertJ · Testcontainers · WireMock · MockMvc.
