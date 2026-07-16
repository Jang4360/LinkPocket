# API 에러 계약 (도메인별 비즈니스 코드 + BE-FE 합의)

> 결정 근거: [decisions/adr-007-domain-error-code-contract.md](../decisions/adr-007-domain-error-code-contract.md)
> **모든 API를 가진 plan은 이 스펙을 따라 자기 도메인의 에러 코드 표를 자기 plan 문서에 채운다.** (plan 템플릿 "에러 코드 계약" 섹션)

## 1. 응답 envelope (공통, 전 도메인 동일 모양)

모든 에러 응답은 이 모양을 따른다. HTTP status는 응답 헤더/status line에 그대로 실리고, body에는 아래 필드가 담긴다.

```json
{
  "code": "AUTH_SESSION_EXPIRED",
  "domain": "AUTH",
  "message": "세션이 만료되었습니다. 다시 로그인해주세요.",
  "traceId": "b3c1...",
  "details": null
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `code` | string | `{DOMAIN}_{REASON}` 형식의 비즈니스 코드. **프론트는 화면 분기에 이 필드를 쓴다.** |
| `domain` | string | 코드의 접두사와 동일(`AUTH`, `LINK`, `CATEGORY` 등). 로깅·필터링용. |
| `message` | string | 사람이 읽는 기본 문구(한국어). **프론트가 그대로 노출해도 되지만, 화면별 문구를 프론트가 재정의할 수도 있다** (아래 3절 표의 "사용자 문구 owner" 참고). |
| `traceId` | string | 관측성 연결용(OpenTelemetry trace ID). 사용자에게 보여줄 수도 있음(문의 시 참조). |
| `details` | object\|null | 도메인별 추가 정보(예: 검증 실패 필드명). 없으면 `null`. |

## 2. 코드 네이밍과 소유권

- 형식: **`{DOMAIN}_{REASON}`** (대문자 스네이크). 예: `AUTH_SESSION_EXPIRED`, `AUTH_OAUTH_STATE_MISMATCH`, `LINK_DUPLICATE_URL`, `CATEGORY_NOT_FOUND`.
- 도메인마다 **자기 `ErrorCode` enum을 소유**한다 (예: `AuthErrorCode`, `LinkErrorCode`). 공통 인터페이스를 구현:

```java
public interface ErrorCode {
    String code();          // "AUTH_SESSION_EXPIRED"
    HttpStatus httpStatus(); // HttpStatus.UNAUTHORIZED
    String defaultMessage(); // "세션이 만료되었습니다..."
}
```

- 도메인 예외는 이 `ErrorCode`를 들고 던지고(`DomainException(ErrorCode code, Object... args)`), **전역 예외 핸들러 하나**가 모든 도메인 예외를 위 envelope로 직렬화한다. 새 도메인은 enum만 추가하면 되고, 직렬화·핸들러 로직은 건드리지 않는다.
- **HTTP status와 비즈니스 코드는 분리된 축이다.** HTTP status는 범용 처리(401→재인증 필요, 404→리소스 없음, 429→재시도)를 위한 것이고, 비즈니스 코드는 "정확히 무슨 일이 났고 화면에 뭘 보여줄지"를 위한 것이다. 같은 401이라도 `AUTH_SESSION_EXPIRED`(조용히 재로그인 유도)와 `AUTH_REFRESH_TOKEN_REUSED`(보안 경고 + 강제 로그아웃)는 화면 처리가 다르다.

## 3. 도메인 에러 코드 표 (plan 문서에 채우는 형식)

각 plan은 실패 조건(Acceptance Criteria의 실패 케이스)마다 아래 표를 채운다:

| 코드 | HTTP status | 화면 처리 | 사용자 문구 owner |
|---|---|---|---|
| `{DOMAIN}_{REASON}` | 4xx/5xx | 토스트 \| 인라인 필드 에러 \| 전체 화면 리다이렉트 \| 조용히 재시도 | BE 기본값 사용 \| FE 재정의 |

**화면 처리 카테고리 (고정 어휘 — 새로 만들지 않는다):**
- **토스트**: 짧은 알림, 사용자는 하던 작업을 계속.
- **인라인 필드 에러**: 폼 입력값 옆에 표시(예: "이미 사용 중인 이메일").
- **전체 화면 리다이렉트**: 로그인 페이지 등으로 강제 이동.
- **조용히 재시도**: 사용자에게 안 보이고 클라이언트가 자동 재시도(예: 네트워크 일시 오류).

## 4. 검증 (CI 게이트로 승격 후보)

- 각 도메인 enum의 `code()`가 `{DOMAIN}_` 접두사로 시작하는지 단위 테스트로 강제.
- 전역 예외 핸들러가 모든 도메인 예외를 이 envelope로만 직렬화하는지 계약 테스트로 검증(임의 500 stack trace 노출 금지).
- (P1 후보) plan 문서의 에러 코드 표와 실제 코드의 enum 상수를 diff하는 스크립트 — 문서-구현 불일치를 CI에서 잡는다.

## 5. 첫 구현 (plan-01)

이 스펙의 프레임워크(공통 `ErrorCode` 인터페이스, envelope, 전역 예외 핸들러)는 **plan-01의 task**로 처음 만들어진다. `AuthErrorCode`가 첫 도메인 enum이다. 구체적 코드 표는 [plan/01-auth-google-oauth.md](../plan/01-auth-google-oauth.md) 참고.
