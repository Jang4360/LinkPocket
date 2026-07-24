---
기능: Google OAuth 로그인 (웹 + 크롬 익스텐션) · 세션 · tenant 경계
관련 축: H(인증·인가) / 주차: 1
상태: 승인 대기(초안)
---

## 목적 (한 줄)
웹과 크롬 익스텐션 두 클라이언트가 Google OAuth로 로그인하고, 이후 모든 API 요청이 **서버가 강제하는 tenant 경계** 안에서만 동작하게 한다.

## 범위
- **포함:** Google OAuth Authorization Code + PKCE, 웹 세션(HttpOnly 쿠키 + 서버 세션 스토어), 익스텐션 device session(`chrome.storage.local`, refresh rotation), `userId` 기반 tenant 강제, 로그인/로그아웃/세션 조회 API.
- **제외:** GitHub 등 추가 OAuth provider, magic link(다이제스트용 — plan-09에서), 관리자 세션 감사 UI, 다중 기기 관리 화면(P1 후보).

## 허용 쓰기 경로 (소급 반영 — [ADR-008](../decisions/adr-008-harness-hardening.md))
- `src/main/java/com/linkpocket/auth/**`, `src/main/java/com/linkpocket/common/**`(공통 에러 프레임워크)
- `src/main/resources/db/migration/V2__*.sql`
- `build.gradle.kts` (신규 의존성 추가만)
- `src/test/java/com/linkpocket/contract/auth/**`는 Claude 소유(Codex 쓰기 금지, 이미 명시됨)

## 승인된 설계 결정 (게이트 ① — [ADR-006](../decisions/adr-006-auth-session-architecture.md))
1. **웹 세션**: HttpOnly+Secure+SameSite 쿠키(세션ID만) + 서버 세션 스토어(DB).
2. **익스텐션 인증**: `chrome.identity.launchWebAuthFlow` + Authorization Code + PKCE.
3. **익스텐션 토큰 보관**: `chrome.storage.local`에 refresh token까지 저장 — **단, rotation·reuse detection·짧은 access TTL이 필수 조건.**
4. **tenant 경계**: 서버가 세션에서 `userId`를 추출해 모든 쿼리에 강제. 클라이언트가 보낸 `userId`류 파라미터는 신뢰하지 않는다.

## 에러 코드 계약 (AUTH 도메인) — 실제 API 구현보다 먼저 확정

스펙: [architecture/api-error-contract.md](../architecture/api-error-contract.md). 이 plan에서 공통 `ErrorCode` 인터페이스·envelope·전역 예외 핸들러를 처음 만들고(task-01a-2), `AuthErrorCode`가 첫 도메인 enum이 된다.

| 코드 | HTTP status | 화면 처리 | 사용자 문구 owner |
|---|---|---|---|
| `AUTH_SESSION_EXPIRED` | 401 | 전체 화면 리다이렉트(로그인 페이지) | BE 기본값 |
| `AUTH_SESSION_INVALID` | 401 | 전체 화면 리다이렉트(로그인 페이지) | BE 기본값 |
| `AUTH_OAUTH_STATE_MISMATCH` | 400 | 토스트("로그인 세션이 유효하지 않습니다. 다시 시도해주세요.") + 로그인 재시작 | BE 기본값 |
| `AUTH_OAUTH_CODE_EXCHANGE_FAILED` | 502 | 토스트("Google 로그인에 실패했습니다. 잠시 후 다시 시도해주세요.") | BE 기본값 |
| `AUTH_REFRESH_TOKEN_INVALID` | 401 | 전체 화면 리다이렉트(익스텐션 재로그인) | BE 기본값 |
| `AUTH_REFRESH_TOKEN_REUSED` | 401 | **전체 화면 리다이렉트 + 보안 경고 토스트**("비정상적인 로그인 시도가 감지되어 모든 세션이 종료되었습니다.") — family 전체 폐기됐음을 사용자에게 알림 | FE 재정의(보안 경고 문구는 FE가 강조 스타일로 표시) |
| `AUTH_FORBIDDEN_RESOURCE` | 403 | 토스트("접근 권한이 없습니다.") — IDOR 방어 발동 시. 리소스 존재 자체를 숨겨야 하는 곳은 `AUTH_RESOURCE_NOT_FOUND`(404)로 대체 | BE 기본값 |
| `AUTH_RESOURCE_NOT_FOUND` | 404 | 인라인("찾을 수 없습니다") 또는 목록에서 조용히 제외 | FE 재정의 |
| `AUTH_PKCE_VERIFICATION_FAILED` | 400 | 토스트 + 로그인 재시작 | BE 기본값 |

이 표는 위 "실패 조건" 절의 각 케이스에 대응한다. **구현 중 실패 조건이 추가되면 이 표도 함께 갱신한다** — 표와 enum 상수가 항상 1:1이어야 한다(architecture/api-error-contract.md 4절 검증 원칙).

## Acceptance Criteria (수용 기준)

**에러 계약**
- [ ] 모든 인증 실패 응답은 [api-error-contract.md](../architecture/api-error-contract.md) envelope(`code`,`domain`,`message`,`traceId`,`details`)을 따른다.
- [ ] `AuthErrorCode`의 모든 상수가 `AUTH_` 접두사로 시작한다(단위 테스트로 강제).
- [ ] 전역 예외 핸들러가 처리하지 못한 예외(500)도 envelope 형태로 나가고 stack trace가 응답 본문에 노출되지 않는다.

**웹 로그인**
- [ ] `GET /oauth2/authorization/google` → Google 동의 화면으로 리다이렉트.
- [ ] 콜백 성공 시 서버가 세션을 생성하고, 응답에 `Set-Cookie: SESSION=...; HttpOnly; Secure; SameSite=Lax`가 포함된다.
- [ ] 로그인 상태에서 `GET /api/me` → `200`과 `{ userId, email, name }`.
- [ ] `POST /api/logout` → 서버 세션 폐기 + 쿠키 만료(`Max-Age=0`).

**익스텐션 로그인**
- [ ] 익스텐션이 PKCE `code_verifier`/`code_challenge`를 생성해 `launchWebAuthFlow`로 인가 요청을 보낸다.
- [ ] 콜백 code를 서버에 교환하면 **device session**이 생성되고 `{ accessToken(TTL≤15분), refreshToken }`을 반환한다.
- [ ] `POST /api/extension/token/refresh`로 access token을 갱신하면, **이전 refresh token은 즉시 폐기**되고 새 refresh token이 발급된다(rotation).
- [ ] 폐기된 refresh token으로 재요청하면 **해당 token family 전체가 폐기**되고 `401`이 반환된다(reuse detection).

**tenant 경계**
- [ ] 인증된 사용자 A의 세션으로, 사용자 B 소유 리소스 ID를 넣어 조회를 시도하면 `403` 또는 `404`(존재 자체를 숨김)를 반환하고 데이터가 노출되지 않는다.
- [ ] 모든 도메인 조회 쿼리는 컨트롤러가 아니라 **서비스/리포지토리 레이어에서 세션의 `userId`를 강제**한다(클라이언트 입력 `userId` 무시).

## 불변식 (항상 참)
- 세션 쿠키는 **HttpOnly**이며 JavaScript에서 접근 불가하다.
- 익스텐션 **access token TTL ≤ 15분**.
- Refresh token은 **1회 사용 후 폐기**되며 재사용 시 family 전체 폐기.
- 웹 세션과 익스텐션 device session은 **독립적으로 폐기 가능**하다(한쪽 로그아웃이 다른 쪽에 영향 없음, 단 "전체 로그아웃"은 예외로 둘 다 폐기).
- 서버는 **모든** 도메인 쿼리에서 인증 세션의 `userId`를 사용하며, 요청 바디/쿼리 파라미터의 `userId`를 절대 신뢰하지 않는다.
- PKCE `state`는 1회용이며 재사용 시 거부한다.

## 실패 조건 (이렇게 되면 실패로 본다)
- 세션 쿠키에 `HttpOnly` 플래그가 없으면 실패.
- refresh token이 rotation 없이 여러 번 재사용 가능하면 실패.
- 폐기된 refresh token 재사용이 감지되지 않고 새 access token이 발급되면 실패(reuse detection 누락).
- 클라이언트가 보낸 `userId` 파라미터로 다른 사용자 데이터를 조회할 수 있으면 실패(IDOR).
- OAuth `state` 불일치 또는 재사용을 서버가 거부하지 않으면 실패.
- 만료되거나 서명이 잘못된 세션/토큰으로 보호된 API에 접근했을 때 `401`이 아니면 실패.

## API 계약 (초안 — 상세는 architecture/openapi로 확정)

| 메서드/경로 | 설명 | 응답 |
|---|---|---|
| `GET /oauth2/authorization/google` | 웹 로그인 시작 | 302 → Google |
| `GET /login/oauth2/code/google` | 웹 콜백 | 302 + `Set-Cookie` |
| `GET /api/me` | 현재 사용자 조회 (웹/익스텐션 공통, 인증 필요) | 200 `{userId,email,name}` / 401 |
| `POST /api/logout` | 웹 로그아웃 | 204 + 쿠키 만료 |
| `POST /api/extension/oauth/callback` | 익스텐션 code 교환 | 200 `{accessToken, refreshToken, expiresIn}` |
| `POST /api/extension/token/refresh` | 익스텐션 토큰 갱신 (rotation) | 200 `{accessToken, refreshToken, expiresIn}` / 401(reuse detected) |
| `POST /api/extension/logout` | 익스텐션 device session 폐기 | 204 |

## 구현 계획 (task = 작은 PR)
1. **task-01a-1 — 공통 에러 프레임워크**: `ErrorCode` 인터페이스, 공통 envelope DTO, `DomainException`, 전역 `@RestControllerAdvice` 예외 핸들러. (이후 모든 도메인이 재사용 — plan-01에서 1회만 구축)
2. **task-01a-2 — User 엔티티 + Flyway V2**: `User(id, googleSub, email, name, createdAt)`, unique constraint on `googleSub`.
3. **task-01b — 웹 OAuth + 세션 스토어**: Spring Security OAuth2 Client(Google), 세션 저장소(JDBC 세션 또는 자체 `Session` 테이블), `GET /api/me`, `POST /api/logout`, `AuthErrorCode` 적용.
4. **task-01c — 익스텐션 PKCE 교환 + device session**: `DeviceSession(id, userId, refreshTokenHash, family, createdAt, revokedAt)`, `/api/extension/oauth/callback`.
5. **task-01d — refresh rotation + reuse detection**: `/api/extension/token/refresh`, family 폐기 로직, `AUTH_REFRESH_TOKEN_REUSED` 응답.
6. **task-01e — tenant 경계 강제 + IDOR 테스트 보강**: 공통 인증 컨텍스트(`@AuthenticationPrincipal` 등)로 `userId` 추출을 표준화, 모든 리포지토리 접근 지점 점검.

## 위험 로직 결정 (합의 완료 — [ADR-006](../decisions/adr-006-auth-session-architecture.md))
- 웹 세션 저장: HttpOnly 쿠키 + 서버 세션 스토어 → ADR-006 결정 1
- 익스텐션 인증 흐름: Authorization Code + PKCE → ADR-006 결정 2
- 익스텐션 토큰 보관: `chrome.storage.local` + refresh rotation/reuse detection(필수 완화책) → ADR-006 결정 3
- tenant 경계: 서버가 세션에서 userId 강제 → ADR-006 결정 4

## 대조 기록 (SDD 증거 — 구현 중 채움)
- AI 계획 중 수정/거절한 것과 이유:
- spec↔구현 누락 건수 · 수정 turn 수:
- IDOR 공격 테스트 결과:
- refresh rotation/reuse detection 검증 결과:
