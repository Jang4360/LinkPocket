# 실수 원장 (mistake ledger)

> AI(또는 사람)가 낸 **반복 가능한 실수**를 한 줄씩 누적한다. 같은 유형이 쌓이면 skill/hook으로 **승격**해 재발을 원천 차단한다.
> 승격 규칙 전체: [development-loop.md](../development-loop.md) · 결정: [decisions/adr-005-mistake-promotion.md](../decisions/adr-005-mistake-promotion.md)

## 어떻게 쓰나

- 실수를 발견할 때마다(리뷰·게이트·postmortem) **한 줄** 추가한다. 길게 쓰지 않는다 — 상세는 postmortem/experiment로.
- **`카테고리`는 재사용한다.** 새로 만들지 말고 기존 것에 붙여야 카운트가 쌓인다.
- 임계값(초기 가설): **같은 카테고리 2회 = 승격 후보, 3회 = 반드시 승격.** 승격 생성은 항상 **사람 승인**.

## 형식

| 날짜 | 카테고리 | 무엇이 잘못됐나 (한 줄) | 잡힌 곳 | 승격 |
|---|---|---|---|---|
| (예) 2026-07-20 | tx-boundary | 외부 HTTP 호출이 트랜잭션 안에 들어감 | Claude 리뷰 | – |
| 2026-07-16 | contract-test-authoring | 계약 테스트 Javadoc의 `**/contract/**`가 `*/`로 주석을 조기 종료 → 컴파일 오류 | Codex 게이트(우회 없이 에스컬레이션) → Claude 교정 | – (1회, 교정만) |
| 2026-07-16 | contract-test-authoring | WireMock 정적 `stubFor(...)`가 기본 포트(8080)로 등록돼 동적 포트 서버(GOOGLE)와 불일치 → 404 | Codex 게이트(우회 없이 에스컬레이션) → Claude 교정(`GOOGLE.stubFor`로 변경) | **승격 후보(2회째)** — development-loop 참고, 사람 승인 대기 |

## 카테고리 예시 (필요하면 자라남)

`tx-boundary`(트랜잭션 경계) · `idempotency`(멱등) · `ssrf` · `authz`(권한/tenant) · `n+1` · `context-drift`(계획 밖 파일 수정) · `hallucinated-api`(존재하지 않는 API) · `weakened-test`(계약 테스트 약화 시도)

## 승격 판정 (2 → 후보, 3 → 필수)

- **절차 반복** → `skill` (`.claude/skills/`)로 규격화 (예: `contract-review` 체크리스트).
- **불변 규칙** → `hook` (`.claude/settings.json`)으로 강제 (예: 보호 경로 write 차단).
- **1회성** → 교정만 하고 원장에만 남긴다(승격 안 함).
- 승격하면 해당 행 `승격` 칸에 `→ skill:name` 또는 `→ hook`을 기록해 닫는다.

> 이 원장은 [cs-learning A섹션](../learning/cs-learning.md)의 "실패→가드레일 보강"을 상시 루프로 돌리는 장치다.
