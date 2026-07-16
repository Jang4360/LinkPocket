# ADR-006: 인증·세션 아키텍처 (웹 세션 · 익스텐션 OAuth · 토큰 보관 · tenant 경계)

- 날짜: 2026-07-16 / 상태: 확정
- **범주: 아키텍처**
- 관련: [plan-01-auth-google-oauth.md](../plan/01-auth-google-oauth.md), [architecture/auth-and-session.md](../architecture/auth-and-session.md)

## 상황

plan-01(Google OAuth 로그인)을 구현하기 전, 위험 로직 4곳을 사람과 논의해 결정했다([plan/README.md](../plan/README.md) 절차). 대상 클라이언트는 **웹**과 **크롬 익스텐션** 둘이며 성격이 다르다(익스텐션은 OAuth 표준상 "public client" — client secret을 안전히 보관 못 함).

## 결정 1 — 웹 세션 저장

**결정: HttpOnly + Secure + SameSite 쿠키에 세션ID만 담고, 서버가 세션 스토어(DB)를 보유한다.**

| 선택지 | 트레이드오프 |
|---|---|
| **HttpOnly 쿠키 + 서버 세션 스토어** (채택) | 서버 상태 필요하지만, 어차피 사용자 DB가 있어 추가 비용 작음 |
| JWT를 쿠키에 직접 저장(stateless) | revoke가 어려움 — 도난 의심 시 서버가 즉시 세션을 못 끊음 |
| localStorage에 토큰 | XSS 취약. 이 서비스는 사용자가 저장한 외부 URL 본문을 렌더링할 수 있어 XSS 표면이 있음 |

**핵심 이유**: XSS로 토큰 탈취 불가 + 서버가 세션을 즉시 강제 종료할 수 있음.

## 결정 2 — 크롬 익스텐션 인증 흐름

**결정: `chrome.identity.launchWebAuthFlow` + Authorization Code + PKCE. 백엔드가 code를 교환해 단기 access token을 발급한다.**

| 선택지 | 트레이드오프 |
|---|---|
| **PKCE** (채택) | 익스텐션에 client secret을 두지 않아도 안전. 구현 항목 하나 추가(code_verifier/challenge) |
| client secret 익스텐션 내장 | secret이 사용자 로컬에 노출 — 유출 위험. OAuth 표준이 public client에 애초에 권장하지 않음 |

**핵심 이유**: 크롬 익스텐션은 OAuth 표준상 public client라 secret을 안전히 보관할 방법이 없다. PKCE가 사실상 유일한 안전한 선택.

## 결정 3 — 익스텐션 토큰 보관 위치

**결정: `chrome.storage.local`에 refresh token까지 저장한다.** (사람 판단으로 사용성 우선 — 재로그인 최소화)

| 선택지 | 트레이드오프 |
|---|---|
| `chrome.storage.session`(access token만, 단기) | 탈취 표면 최소, 대신 브라우저 재시작마다 재인증 필요(사용성 저하) |
| **`chrome.storage.local`에 refresh token까지** (채택) | 재로그인 불필요, 사용성 좋음. **대신 로컬 디스크에 장기 자격증명이 상주** — 기기 탈취·악성 확장 프로그램에 노출 위험이 `session` 대비 크다 |

**핵심 이유**: 사용자가 매번 재로그인하지 않는 사용성을 우선했다. **이건 보안보다 사용성을 명시적으로 택한 결정**이며, 아래 완화책으로 위험을 낮춘다.

**필수 완화책 (이 결정을 채택하는 조건):**
- Refresh Token **rotation**: refresh할 때마다 새 토큰 발급, 이전 토큰은 즉시 폐기.
- **Reuse detection**: 폐기된 refresh token이 재사용되면 그 token family 전체를 폐기하고 재인증을 요구한다(탈취 징후로 간주).
- Access token은 **짧은 TTL**(예: 15분)로 유지해, 탈취돼도 피해 창을 줄인다.
- 사용자가 "다른 기기에서 로그아웃" 등으로 개별 device session을 폐기할 수 있게 한다([architecture/auth-and-session.md](../architecture/auth-and-session.md) 이미 명시된 device session 분리 원칙과 정합).

## 결정 4 — tenant(사용자) 경계 강제

**결정: 서버가 인증된 세션에서 `userId`를 추출해 모든 쿼리에 강제한다. 클라이언트가 보낸 `userId`류 파라미터는 신뢰하지 않는다.**

이 결정은 협상 여지가 있는 트레이드오프가 아니라 보안 기본(IDOR 방지)이라 별도 대안 비교 없이 채택했다. 논의보다 **계약 테스트로 못박는다** — 다른 사용자 ID로 바꿔 호출하는 IDOR 공격 테스트가 plan-01 계약에 포함된다.

## 트레이드오프 종합

- 결정 3(익스텐션 토큰 로컬 저장)이 이 ADR에서 유일하게 보안보다 사용성을 우선한 지점이다. rotation·reuse detection·짧은 access TTL이 없으면 이 결정은 위험하다 — **완화책은 선택이 아니라 필수 조건**으로 plan-01 계약에 포함한다.

## 재검토 조건

- 실제 사용자 피해 사례(익스텐션 토큰 탈취)가 관측되면 `chrome.storage.session`으로 전환한다.
- Alpha 이후 "다른 기기 로그아웃" 등 세션 관리 UX 요구가 커지면 device session 관리 화면을 P0로 승격한다.

## 근거 (외부)

- Chrome Extension OAuth 공식 가이드, IETF OAuth 2.0 Security BCP(RFC 9700) — PKCE·public client 권장.
- 링크: [reference/sources.md](../reference/sources.md)

## 면접 답변 요지

> "웹은 HttpOnly 쿠키+서버 세션으로 XSS·즉시 revoke를 확보했다. 크롬 익스텐션은 OAuth 표준상 public client라 PKCE를 썼다. 익스텐션 토큰은 사용성을 위해 local storage에 refresh token까지 뒀는데, 이건 보안보다 사용성을 명시적으로 택한 결정이라 rotation·reuse detection·짧은 access TTL을 필수 조건으로 걸었다. tenant 경계는 서버가 세션에서 뽑은 userId로 강제하고 IDOR 공격 테스트로 검증했다."
