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
| 2026-07-26 | contract-test-authoring | SSRF test-only 허용 목록에 `127.0.0.1`을 하드코딩했는데 `WireMockServer.baseUrl()`이 실제로는 `localhost`를 반환 → 정상 origin 요청까지 SSRF_BLOCKED | Codex 게이트(우회 없이 에스컬레이션) → Claude 교정(하드코딩 대신 `ORIGIN.baseUrl()`에서 직접 파싱) | **승격 완료(3회째)** → [development-loop.md 규칙 7](../development-loop.md) |
| 2026-07-26 | adr-implementation-drift | ADR-006 결정 1이 "서버 세션 스토어(DB)"를 명시했는데 실제 구현은 Tomcat 기본 인메모리 세션 — 계약 테스트가 영속성을 검증 안 해 안 잡힘 | plan-01 회고(사람 주도 문제 재정의 세션) | – (1회, 후속 task로 수정 결정) |
| 2026-07-27 | contract-test-authoring | `@TestConfiguration` 중첩 클래스를 상속 원본(추상 기반 클래스)에 두면 서브클래스가 자동 인식 못 함(`declaredClasses`는 실행되는 서브클래스만 봄) → Fake 빈 미등록으로 전 테스트가 `NoSuchBeanDefinitionException` | Claude 직접 실행(verify.sh) | – (4회째, 이미 승격된 카테고리라 규칙 7에 흡수 — 명시적 `@Import` 필요성만 새로 확인) |
| 2026-07-27 | jdbc-type-mapping | Codex 구현이 `resultSet.getObject(col, Instant.class)`로 `timestamptz`를 읽음 — pgjdbc가 이 직접 변환을 지원 안 해 매 호출이 `DataIntegrityViolationException`으로 500 | Claude 리뷰(verify.sh 직접 실행 중 발견, `OffsetDateTime.toInstant()`로 교정) | – (1회, 교정만) |
| 2026-07-27 | silent-exception-handling | `GlobalExceptionHandler.handleUnexpectedException`이 예외를 로깅 없이 삼켜 500 원인 추적 불가 — plan-04 리뷰 중 원인 파악에 임시 `printStackTrace()` 우회가 필요했음(plan-01부터 있던 기존 코드, plan-04 범위 밖이라 이번엔 미수정) | Claude 리뷰(verify.sh 디버깅 중 발견) | – (1회, 후속 task 여부 사람 판단 대기) |
| 2026-07-27 | adr-implementation-drift | ADR-011 결정 1이 "DNS 해석 후 IP 검증"을 명시했는데 실제 구현은 검증용 DNS 조회와 실제 연결용 DNS 조회가 분리돼 있어 DNS rebinding(TOCTOU)에 무방비. 같은 코드의 `isBlockedAddress`가 IPv4(4바이트)만 검사해 IPv6 사설·loopback·link-local 대역은 전부 미검사로 통과 — 계약 테스트가 리터럴 IP만 검증해 안 잡힘 | exp-06(SSRF 회귀 실험) 설계 중 코드 재검토로 발견, Claude 리뷰 | **승격 후보(2회째)** — development-loop 참고, 사람 승인 대기 |

## 카테고리 예시 (필요하면 자라남)

`tx-boundary`(트랜잭션 경계) · `idempotency`(멱등) · `ssrf` · `authz`(권한/tenant) · `n+1` · `context-drift`(계획 밖 파일 수정) · `hallucinated-api`(존재하지 않는 API) · `weakened-test`(계약 테스트 약화 시도)

## 승격 판정 (2 → 후보, 3 → 필수)

- **절차 반복** → `skill` (`.claude/skills/`)로 규격화 (예: `contract-review` 체크리스트).
- **불변 규칙** → `hook` (`.claude/settings.json`)으로 강제 (예: 보호 경로 write 차단).
- **1회성** → 교정만 하고 원장에만 남긴다(승격 안 함).
- 승격하면 해당 행 `승격` 칸에 `→ skill:name` 또는 `→ hook`을 기록해 닫는다.

> 이 원장은 [cs-learning A섹션](../learning/cs-learning.md)의 "실패→가드레일 보강"을 상시 루프로 돌리는 장치다.
