---
기능: 안전한 URL fetch + 본문 추출 + 상태 전이 (AI 없이)
관련 축: D. 외부 URL 수집: 네트워크·보안 / 주차: 2
상태: 승인 대기(초안)
---

## 목적 (한 줄)
저장된 Link의 URL을 안전하게(SSRF 방어) 가져와 본문을 추출하고, 그 결과를 `PENDING → FETCHED / READY_WITHOUT_CONTENT / FAILED` 상태로 정확히 반영한다.

## 범위 — 스코프 판단(사람 대조 필요)
- **포함:** SSRF 방어 fetch(`SafeFetcher`), HTML 본문 추출([html-extraction 아티클](../learning/articles/html-extraction-readability-vs-jsoup-vs-trafilatura.md) 근거로 readability4j), 상태 전이 로직, 실패 사유 분류·기록.
- **제외:** 요약·임베딩·색인(plan-04), 실제 fetch를 **언제** 트리거할지(job polling/worker 인프라는 [architecture/async-pipeline.md](../architecture/async-pipeline.md)대로 plan-04 소관).
- **스코프 판단(Claude 제안, 사람 확인 필요):** `architecture/async-pipeline.md`가 "Job polling"을 plan-04 소관으로 명시하고 있어, 이 plan은 **호출 가능한 서비스**(`LinkFetchService.fetchAndExtract(linkId)`)까지만 만든다. 공개 API 엔드포인트는 없다 — 언제·누가 이 메서드를 호출할지는 plan-04에서 worker로 연결한다. 계약 테스트는 이 서비스 메서드를 직접 호출해 검증한다(WireMock으로 외부 URL 서버 대역).

## Acceptance Criteria (수용 기준)
- [ ] 안전한 `http(s)` URL fetch에 성공하면 Link 상태가 `FETCHED`로 전이하고 추출된 제목·본문이 저장된다.
- [ ] hostname이 사설(RFC1918)·loopback·link-local·클라우드 메타데이터(`169.254.169.254`) IP로 해석되면 fetch 자체를 시도하지 않고 즉시 `FAILED`(사유: `SSRF_BLOCKED`)로 전이한다.
- [ ] 최초 hostname은 공인 IP였으나 redirect 대상이 위 차단 대역으로 해석되면, 그 redirect를 따라가지 않고 `FAILED`(`SSRF_BLOCKED`)로 전이한다.
- [ ] connect timeout(3s)을 초과하면 재시도 후에도 실패 시 `FAILED`(사유: `FETCH_TIMEOUT`, 재시도 가능 표시).
- [ ] `429`/일부 `5xx`는 최대 2회까지 backoff+jitter로 재시도한다. 재시도 후에도 실패하면 `FAILED`(재시도 가능 표시), `4xx`는 즉시 `FAILED`(재시도 불가 표시)로 전이한다.
- [ ] 압축 해제 후 본문이 5MB를 넘으면 그 시점에서 fetch를 중단하고 `FAILED`(사유: `CONTENT_TOO_LARGE`)로 전이한다.
- [ ] HTML은 정상적으로 받았지만 본문 추출이 실패하면(추출 라이브러리가 본문 블록을 못 찾음) `READY_WITHOUT_CONTENT`로 전이한다 — fetch 실패(`FAILED`)와 구분한다.
- [ ] 위 모든 실패 경로에서 Link row 자체는 삭제되지 않고 URL·fallback title은 그대로 보존된다.
- [ ] 같은 Link에 `fetchAndExtract`가 동시에 두 번 호출되면, 한쪽만 실제로 fetch를 수행하고 다른 쪽은 즉시 no-op으로 반환한다(중복 fetch 방지).

## 불변식 (항상 참이어야 하는 것)
- 실제 네트워크 연결 전에 hostname의 해석된 IP를 반드시 검증한다(불변조건: fetch 전 SSRF 검증 없이 연결하지 않는다).
- redirect를 따라갈 때마다 매번 같은 IP 검증을 반복한다 — 최초 1회만 검증하고 이후 redirect는 무검증으로 따라가지 않는다.
- fetch·추출 실패가 Link 삭제로 이어지지 않는다([invariants.md](../product/invariants.md) 전역 불변조건 재확인).
- 본문 크기 상한은 압축 해제 **후** 크기를 기준으로 한다(압축 해제 전 크기만 보면 zip bomb류를 놓친다).
- `link.status`가 `PENDING`일 때만 fetch를 시작할 수 있다 — `PENDING→FETCHING` 전이는 원자적 조건부 UPDATE로만 일어난다(ADR-011 결정 4).

## 실패 조건 (이렇게 되면 실패로 본다)
- 사설/loopback/link-local/메타데이터 IP로 fetch를 실제로 시도하면 실패(설계 위반).
- redirect 대상의 IP를 재검증하지 않고 따라가면 실패.
- 4xx 응답에 재시도를 시도하면 실패(재시도 예산 낭비).
- 5MB를 초과하는 본문을 끝까지 메모리에 올리면 실패.
- fetch 실패 시 Link row나 URL·fallback title이 함께 사라지면 실패.
- 동시 호출 두 개가 모두 실제 fetch를 수행하면(중복 요청·중복 상태 전이 시도) 실패.

## 위험 로직 결정 (합의 완료 — [ADR-011](../decisions/adr-011-safe-fetch-ssrf-timeout-retry.md))
- SSRF 방어 깊이: DNS 해석 후 IP 검증 + redirect마다 재검증 → ADR-011 결정 1
- Timeout 구조: connect 3s / 응답(TTFB) 5s / 읽기 전체 15s → ADR-011 결정 2
- 재시도·크기 제한: timeout·429·일부 5xx만 최대 2회 재시도, 본문 5MB 상한 → ADR-011 결정 3
- 중복 fetch 방지: `link.status` 원자적 조건부 UPDATE(`PENDING→FETCHING`)로 선점 → ADR-011 결정 4

## 기술 선택 (pre-hoc 아티클 근거 — 결정은 이 plan에서)
[HTML 본문 추출 라이브러리 비교](../learning/articles/html-extraction-readability-vs-jsoup-vs-trafilatura.md) 아티클의 추천대로 **readability4j**를 채택한다 — 별도 프로세스 없이 JVM 안에서 처리되고, 실패해도 `READY_WITHOUT_CONTENT` 불변식만 지키면 위험이 낮다.

## 에러 코드 계약 — 해당 없음(공개 API 없음)
이 plan은 공개 API를 노출하지 않는다(위 스코프 판단). 대신 **내부 실패 사유(`FetchFailureReason`)** 를 정의해 `link.status`·`link.failure_reason` 컬럼에 기록한다.

| 사유 코드 | 의미 | 재시도 가능 여부 |
|---|---|---|
| `SSRF_BLOCKED` | 대상 IP가 차단 대역 | 아니오(URL 변경 없이는 무의미) |
| `FETCH_TIMEOUT` | connect/응답/읽기 timeout | 예 |
| `HTTP_CLIENT_ERROR` | 4xx 응답 | 아니오 |
| `HTTP_SERVER_ERROR` | 재시도 후에도 실패한 5xx/429 | 예(다음 수동 재처리 때) |
| `CONTENT_TOO_LARGE` | 압축 해제 후 5MB 초과 | 아니오(URL 자체 문제) |
| `EXTRACTION_FAILED` | 본문 추출 실패(fetch는 성공) → `READY_WITHOUT_CONTENT`로 전이, `FAILED` 아님 | 해당 없음 |

## API 계약 — 해당 없음
공개 API는 plan-04에서 job 트리거와 함께 노출한다. 이 plan의 계약은 `LinkFetchService.fetchAndExtract(UUID linkId)` 메서드 시그니처와 위 상태 전이 결과다.

## 구현 계획 (AI가 따를 단계)
1. **실패 테스트 먼저** — SSRF 차단(사설 IP·redirect 우회), timeout 3종, 재시도 정책(4xx 즉시/5xx 제한 재시도), 크기 상한, 추출 실패 시 `READY_WITHOUT_CONTENT` 전이.
2. Flyway 마이그레이션 — `link`에 `failure_reason text`, `fetched_at timestamptz` 컬럼 추가, `status`에 `FETCHING` 값 허용(V5).
3. `SafeFetcher` — Apache HttpClient5 위에 커스텀 DNS resolver(IP 검증) + redirect handler(hop마다 재검증) + 3구간 timeout 설정.
4. `ContentExtractor` — readability4j 래핑, 실패 시 예외가 아니라 "추출 결과 없음"을 반환(호출자가 `READY_WITHOUT_CONTENT`로 매핑).
5. `LinkFetchService.fetchAndExtract(linkId)` — 먼저 `UPDATE link SET status='FETCHING' WHERE id=? AND status='PENDING'`으로 선점(0 row면 즉시 반환) → fetch·추출 → 최종 상태 전이, `FetchFailureReason` 기록.

<AI가 제안한 계획이다 — 사람이 대조·수정한다. 특히 "스코프 판단"(공개 API 없음)이 맞는지 확인 필요.>

## 대조 기록 (SDD 증거)
- AI 계획 중 수정/거절한 것과 이유:
- spec↔구현 누락 건수 · 수정 turn 수:
- SSRF 공격 테스트(사설 IP·redirect 우회·DNS rebinding) 결과:
