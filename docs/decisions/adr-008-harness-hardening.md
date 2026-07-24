# ADR-008: 하네스 강화 검토 — 채택/보류 판정 (외부 제안 리뷰)

- 날짜: 2026-07-23 / 상태: 확정
- **범주: 개발 프로세스 (하네스 강화)**
- 관련: [development-loop.md](../development-loop.md), [conditional-tech-adoption.md](conditional-tech-adoption.md), [plan/README.md](../plan/README.md)

## 상황

외부에서 LinkPocket의 AI 하네스 구조(`.ai/PROJECT.md`, `POLICY.md`의 R0~R4 실행 위험 등급, `check-scope.sh`/`check-secrets.sh`, `verify.sh` fast/full 분리, `deploy.yml` 등)를 도입하자는 제안을 검토했다. 그대로 받아들이지 않고 항목별로 채택·수정·보류를 판정했다.

## 판정 중 정정된 사실

제안은 "main이 여전히 Branch not protected"라고 주장했으나, 이는 **classic Branch Protection API**(`/branches/main/protection`, 404)로 확인한 오판이었다. 실제로는 **Rulesets API**로 ruleset(`enforcement: active`, PR 필수·`verify` status check·deletion/force-push 차단)이 걸려 있었다. 다만 검토 과정에서 **실제 문제**를 하나 발견했다 — `required_approving_review_count: 1`이 걸려 있어 1인 개발자가 자기 PR을 승인할 수 없어 PR #6이 실제로 막혀 있었다(`mergeStateStatus: BLOCKED`). 이건 제안과 무관하게 즉시 0으로 조정했다.

## 결정 — 항목별 판정

| 제안 | 판정 | 근거 |
|---|---|---|
| `.ai/PROJECT.md`(목적·성공·비범위·불변조건 1페이지) | **채택(내용) / 반려(위치)** | 내용은 실제 공백(불변조건이 여러 문서에 흩어짐) — 채택해 [product/invariants.md](../product/invariants.md)로. `.ai/`라는 별도 최상위 트리는 반려 — `docs/`와 이중 정보구조가 생겨 라우터 원칙([ADR-004](adr-004-ai-context-files.md): 참조는 한 단계) 위반. |
| POLICY.md의 R0~R4 위험 등급 | **보류** | staging/production 자체가 아직 없어 R2·R3 대상이 실재하지 않음 — [conditional-tech-adoption.md](conditional-tech-adoption.md)에 신호 조건으로 기록. STOP CONDITIONS(정지 조건)만 지금 채택. |
| STOP CONDITIONS 목록 | **채택** | [development-loop.md](../development-loop.md)에 신설. 역할 분리와 별개로 "실행 자체가 위험한 상황"을 판단하는 축이라 기존 AI 규칙에 없던 실제 공백. |
| plan 템플릿의 `허용 쓰기 경로` | **채택** | plan에 이미 범위(포함/제외)는 있었으나 diff로 검사 가능한 형태가 아니었음. [plan/README.md](../plan/README.md) 템플릿에 추가, plan-01에 소급 반영. |
| `check-secrets.sh` 신설 | **채택** | OAuth secret·DB 자격증명이 실제로 다뤄지는 시점이라 즉시 가치 있음. pre-push·CI에 편입. |
| `check-scope.sh` 신설 | **보류** | `허용 쓰기 경로`가 여러 plan에서 반복 활용되고 수동 대조 부담이 실측될 때 도입. |
| `verify.sh` `--fast`/`--full` 분리 + pre-commit | **보류** | 계약 테스트가 아직 6개뿐이라 pre-commit 마찰이 실질적이지 않음. 스위트가 커져 지연이 체감되면 도입. |
| 보호 인프라 변경에 코드 오너 승인 강제 | **채택(CODEOWNERS)** | 별도 스크립트 불필요 — ruleset의 `require_code_owner_review`가 이미 켜져 있어 `.github/CODEOWNERS`만 추가하면 GitHub 네이티브로 강제됨. |
| `deploy.yml` | **보류** | staging URL·immutable artifact·rollback 대상·production 승인자가 없는 상태에서 만들면 형식뿐인 안전장치. [release-checklist.md](../release-checklist.md)를 운영 기준으로 유지. |

## 변경 사항 (이 ADR로 실제 반영된 것)

- `docs/product/invariants.md` 신설 — 1페이지 북극성(목적·성공·비범위·전역 불변조건·문서 우선순위).
- `docs/development-loop.md` — STOP CONDITIONS 섹션 신설, 보호 경로에 `check-secrets.sh`·CODEOWNERS 추가.
- `scripts/check-secrets.sh` 신설 — 비밀 파일 패턴(`.env`·`.pem`·`.key`·`id_rsa*`) + 코드 내 비밀 패턴(private key·AWS·Google client secret·일반 key=value) 탐지. `.githooks/pre-push`·CI에 편입.
- `.github/CODEOWNERS` 신설 — 보호 경로 전체를 코드 오너 리뷰 대상으로 등록.
- `docs/plan/README.md` 템플릿 — `허용 쓰기 경로` 섹션 추가. `docs/plan/01-auth-google-oauth.md`에 소급 반영.
- `docs/decisions/conditional-tech-adoption.md` — 보류 항목 4개(R0~R4, verify fast/full, deploy.yml, check-scope.sh)를 도입 신호와 함께 등재.
- `CLAUDE.md`/`AGENTS.md` — invariants.md를 최우선 read로, 보호 경로·정지조건 포인터 갱신.

## 트레이드오프

- CODEOWNERS + 승인 1 조합은 1인 개발자에게 자기잠금 위험이 있다 — 이번에 승인 카운트를 0으로 낮춰 회피했다. CODEOWNERS의 code owner review 요구가 동일 문제를 일으키는지는 다음 보호 경로 변경 PR에서 실측 확인한다.
- check-secrets.sh는 정규식 기반이라 완전하지 않다(우회 가능한 인코딩된 비밀 등) — 완벽한 스캐너가 아니라 흔한 실수를 잡는 완화책으로 취급한다.

## 재검토 조건

- CODEOWNERS 요구가 실제로 머지를 막으면(1인 개발자에게 code owner review도 승인처럼 자기잠금이면) bypass list에 본인을 추가하거나 이 요구를 끈다.
- check-secrets.sh가 false positive를 반복 유발하면 패턴을 좁히거나 예외 목록을 둔다.

## 면접 답변 요지

> "외부에서 하네스 개선 제안을 받았을 때 그대로 적용하지 않고, 현재 상태를 GitHub API로 직접 재검증해 제안의 사실 오류(브랜치 보호 미설정 주장)를 잡아냈고, 그 과정에서 실제 문제(1인 개발자에게 승인 1이 걸려 PR이 막힌 것)를 발견해 고쳤다. 제안 항목은 '지금 필요한 것'과 '아직 대상이 없는 것'으로 나눠 채택·보류를 판정하고 근거를 ADR로 남겼다 — 과설계를 피하는 것도 설계 판단이라고 본다."
