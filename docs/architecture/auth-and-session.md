# 인증·세션 경계 설계

> 출처: `learning/cs-learning.md`에서 분리 (2-H 두 클라이언트 세션 경계)
> 전제: web·extension·magic link라는 서로 다른 client와 사용자 데이터 경계를 안전하게 연결한다. 로그인은 우선 Google OAuth 단일 ([decisions/기술스택.md](../decisions/기술스택.md) 2-6절).

## 두 클라이언트의 세션 경계

```text
Web
OAuth callback → server session/refresh cookie(HttpOnly) → CSRF-protected API

Chrome Extension
chrome.identity.launchWebAuthFlow
→ Authorization Code + PKCE
→ one-time code exchange
→ extension 전용 device session + 짧은 access token
```

- 장기 Refresh Token을 `chrome.storage.local`에 그대로 넣는 설계를 기본값으로 확정하지 않는다. 가능한 경우 access token은 memory 또는 `chrome.storage.session`에 두고, 장기 세션은 서버의 device-session record·rotation·reuse detection으로 통제한다.
- web/extension session은 `sessionId`, `clientType`, `deviceName`, `createdAt`, `lastUsedAt`, `revokedAt`, token family를 분리해 사용자가 개별 폐기할 수 있게 한다.
- JWT blacklist를 모든 요청의 Redis 조회로 시작하지 않는다. 짧은 access TTL+서버 session version을 baseline으로 두고, 즉시 폐기가 필요한 위험과 조회 비용을 비교한다.
- magic link는 token을 server-side nonce와 교환한 뒤 URL에서 즉시 제거하고 Referrer-Policy·로그 마스킹을 적용한다. 계정 export·삭제·보안 설정 변경 같은 민감 작업은 magic link만으로 허용하지 않고 재인증한다.

## 인증·인가 불변식

- 인증(로그인 성공)과 인가(`이 사용자가 이 link/vector를 읽을 수 있는가`)를 분리하고, 모든 query에서 tenant를 **서버가 결정**한다. 모델 입력으로 권한을 결정하지 않는다.
- JWT: issuer/audience/exp/nbf 검증, 짧은 access token, 최소 claim, key rotation 계획.
- Refresh Token rotation: refresh 때마다 새 token 발급, 이전 token hash·family·device session 폐기. 이전 token 재사용 시 token family 전체 폐기.
- OAuth Authorization Code+PKCE: public client인 extension은 PKCE S256, one-time state/nonce, exact redirect URI allowlist.
- CSRF vs XSS: cookie 인증 변경 요청에는 CSRF 방어, Authorization header token에는 XSS·token 탈취 방어.

## 검증할 공격 테스트

IDOR·tenant leakage, 만료·잘못된 aud/iss·변조 token 거부, refresh token reuse 시 family 폐기, PKCE code injection·state mismatch·open redirect, magic link 재사용·redirect 조작, 한 client 로그아웃/탈취가 다른 client에 미치는 범위.

> 자체 API rate limiting(로그인·OAuth callback replay 제한 포함)은 [operations/rag-cost-and-rate-limits.md](../operations/rag-cost-and-rate-limits.md) 3절 참고.
