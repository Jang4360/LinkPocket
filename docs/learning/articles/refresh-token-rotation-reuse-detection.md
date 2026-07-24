---
주제: Refresh Token rotation과 재사용 탐지
cs-learning 축: H. 인증·인가와 세션 / Refresh Token rotation
작성일: 2026-07-24 / 상태: 초안
---

LinkPocket 크롬 익스텐션은 재로그인을 줄이려고 refresh token을 `chrome.storage.local`에 둔다([ADR-006](../../decisions/adr-006-auth-session-architecture.md) 결정 3 — 보안보다 사용성을 명시적으로 택한 유일한 지점). 문제는 로컬 디스크에 상주하는 장기 자격증명은 언젠가 새어나간다는 것이다. 악성 확장 프로그램이나 기기 탈취로 refresh token 하나가 복사돼도, 서버 입장에서 도둑의 refresh 요청과 주인의 refresh 요청은 **똑같이 유효한 토큰을 들고 온 정상 요청**으로 보인다. 토큰 자체를 검증하는 것만으로는 이 둘을 구분할 방법이 없다.

> Refresh token rotation은 토큰을 오래 살리는 편의 기능이 아니라, **탈취를 사후에 탐지할 수 있게 토큰에 "한 번 쓰면 끝"이라는 소모성을 부여하는 일**이다.

## 개념 자리잡기

범위를 못박자. 여기서 다루는 것은 (1) refresh할 때마다 새 refresh token을 발급하고 이전 것을 즉시 폐기하는 `rotation`, (2) 이미 폐기된 토큰이 다시 오면 탈취로 간주하고 그 토큰이 속한 `token family`(=하나의 device session) 전체를 폐기하는 `reuse detection`이다. access token 발급 자체나 OAuth 코드 교환(PKCE)은 별도 소항목이므로 여기서는 제외한다.

핵심 데이터 모델은 `DeviceSession(id, userId, refreshTokenHash, family, createdAt, revokedAt)` 한 줄에 담긴다. **토큰 원문은 저장하지 않고 hash만** 둔다(DB가 새도 토큰이 안 새게). 계약은 이렇게 고정돼 있다:

```java
// TokenRotationContractTest — family_revocation_invalidates_even_the_latest_unused_token
performRefresh(refresh1).andExpect(status().isOk());          // 1회 정상 회전 → refresh2 발급
performRefresh(refresh1).andExpect(status().isUnauthorized()); // 소비된 refresh1 재사용 → family 폐기 트리거
performRefresh(refresh2)                                       // 아직 안 쓴 최신 토큰조차
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_INVALID"));
```

함께 볼 신호: `reuse_detection_count`(재사용 탐지 건수), `family_revocation_count`(family 전체 폐기 발생 수), `false_family_revocation`(정상 사용자가 억울하게 강제 로그아웃된 수), `access_token_ttl`(불변식 ≤ 900초).

## 어떻게 동작하는가

**회전은 refresh를 1회용 소모 이벤트로 만든다.**
`POST /api/extension/token/refresh`가 유효한 refresh token을 받으면 새 access token과 **새 refresh token**을 발급하고, 방금 쓴 토큰은 같은 트랜잭션에서 `revokedAt`을 찍어 소비 처리한다. 그래서 정상 흐름에서 refresh token은 항상 딱 한 번만 성공한다(`refresh2 != refresh1`).

**재사용은 "이미 소비된 토큰이 또 왔다"는 사실로 탈취를 추론한다.**
정상 클라이언트는 회전 후 새 토큰을 저장하고 옛 토큰을 버린다. 이미 `revokedAt`이 찍힌 토큰이 다시 오면, 그 토큰을 아직 들고 있는 누군가(=도둑, 또는 회전 결과를 받지 못한 주인)가 있다는 뜻이다. 서버는 이를 탈취 징후로 보고 `401 AUTH_REFRESH_TOKEN_REUSED`를 반환한다.

**폐기는 토큰 하나가 아니라 family 전체를 끊는다.**
도둑과 주인 중 누가 "진짜"인지 서버는 모른다. 그래서 안전하게 그 device session(family)에서 파생된 **모든** 토큰을 무효화하고 재인증을 요구한다. 이 때문에 family가 죽은 뒤엔 아직 한 번도 안 쓴 최신 refresh token조차 `AUTH_REFRESH_TOKEN_INVALID`가 된다 — reuse가 아니라 이미 죽은 family라서 invalid다(계약이 이 둘을 코드로 구분한다).

## 어디서 무너지는가

직관적으로는 "소비된 토큰이 오면 family를 폐기" 한 줄이면 끝처럼 보인다. **하지만 정상 사용자도 같은 refresh token을 두 번 보낼 수 있다.** 익스텐션이 access token 만료 직후 두 개의 API 호출을 거의 동시에 쏘면, 둘 다 같은(아직 유효한) refresh token으로 refresh를 트리거한다. 하나는 회전에 성공하고, 다른 하나는 방금 소비된 토큰을 들고 와 **reuse로 오탐**된다 → family 폐기 → 아무 잘못 없는 사용자가 강제 로그아웃된다. rotation을 넣으면 오히려 이런 race가 새 실패 모드로 생긴다.

AI가 생성한 구현에서 자주 나는 실패도 여기 몰려 있다. ① 회전과 폐기를 별도 쿼리로 처리해 **원자성이 없어**, 동시 요청이 둘 다 "유효"로 읽고 둘 다 회전에 성공한다(토큰이 갈라져 family가 둘이 됨). ② reuse를 탐지하되 **해당 토큰 하나만 폐기**하고 family는 살려둬, 도둑이 먼저 회전해버리면 주인만 막히고 도둑은 계속 유효하다(계약의 세 번째 테스트가 정확히 이걸 잡는다). ③ 토큰 **원문을 DB에 저장**해 naive한 green은 통과하지만 불변식(hash만 저장)을 깬다. 셋 다 "성공 케이스 테스트"만 보면 초록불이다.

## 무엇을 보고 판단하는가

성공률만 보면 이 문제는 숨는다 — refresh 성공률이 99%여도, 그 1%가 전부 정상 사용자의 race 오탐이면 rotation이 사용성을 갉아먹고 있는 것이다. 진짜로 봐야 할 것은 `false_family_revocation`(정상 사용자가 reuse 오탐으로 폐기된 비율)과 `reuse_detection_count`의 분리다. 후자가 0에 수렴하는데 전자가 튀면 탐지 로직이 아니라 동시성 설계를 의심한다.

cs-learning H축의 `검증·기록할 증거`("이전 token 재사용 시 token family 전체 폐기")는 세 개의 계약 테스트로 고정돼 있다: 정상 회전(`refresh2 != refresh1`), 소비 토큰 재사용 → `AUTH_REFRESH_TOKEN_REUSED`, family 폐기 후 미사용 최신 토큰 → `AUTH_REFRESH_TOKEN_INVALID`. 여기에 동시 refresh 부하 테스트(같은 토큰 N개 동시 전송 시 `false_family_revocation`) 원본 수치를 붙이면 "구현했다"가 아니라 "race까지 측정했다"는 증거가 된다.

## 선택지가 있다면

| 선택지 | 좋아지는 것 | 비싸지는 것 |
| --- | --- | --- |
| 고정 refresh token (회전 없음) | 구현 단순, race 자체가 없음 | 탈취 시 무한정 유효 — 탐지·완화 수단이 전무 |
| 회전 + 소비 토큰 하나만 폐기 | 탈취 창 축소, 오탐 없음 | 도둑이 먼저 회전하면 탐지 실패(주인만 막힘) |
| 회전 + family 폐기 (채택) | 탈취를 탐지하고 device session 전체 차단 | 정상 동시 refresh race가 오탐 → 강제 로그아웃 |
| + 짧은 grace window (직전 토큰 N초 재사용 허용) | race 오탐을 실질적으로 제거 | 탈취 탐지가 그 창만큼 지연됨 |

LinkPocket은 ADR-006이 요구한 대로 **회전 + family 폐기**를 채택한다(완화책은 선택이 아니라 로컬 저장을 정당화하는 필수 조건이므로). 다만 race 오탐이 `false_family_revocation`으로 실제 관측되면, family 폐기를 유지한 채 직전 토큰에 한해 짧은 grace window를 얹는 방향을 재검토한다. **탈취 탐지의 민감도와 정상 사용자의 세션 연속성은 동시에 최대화할 수 없다 — grace window는 이 둘 사이의 눈금을 옮기는 손잡이일 뿐, 없애는 장치가 아니다.**

## LinkPocket에 적용 · 남길 증거

- **적용 위치:** 1주차 / plan-01 task-01d(`/api/extension/token/refresh`, family 폐기 로직) / `DeviceSession` 리포지토리.
- **남길 증거:** 세 개의 rotation 계약 테스트 red→green 커밋 + 동시 refresh 부하 시 `false_family_revocation` 원본 수치 + reuse 탐지 시 family 전체 폐기를 확인한 통합 테스트.
- **면접 한 줄:** "로컬에 refresh token을 둔 사용성 결정을 rotation·reuse detection으로 방어했고, family 폐기가 정상 사용자 동시 refresh를 오탐하는 race까지 측정해 트레이드오프를 문서화했다."
