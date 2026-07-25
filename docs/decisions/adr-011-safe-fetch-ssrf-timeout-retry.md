# ADR-011: 안전한 URL fetch — SSRF 방어·timeout 구조·재시도·크기 제한

- 날짜: 2026-07-25 / 상태: 확정
- **범주: 아키텍처**
- 관련: [plan-03-safe-fetch-extract.md](../plan/03-safe-fetch-extract.md), [기술스택.md](기술스택.md) 2-5절(Apache HttpClient 5)

## 상황

plan-03(SSRF-safe fetch + 본문 추출)을 구현하기 전, 위험 로직 3곳을 사람과 논의해 결정했다([plan/README.md](../plan/README.md) 절차). LinkPocket이 사용자가 제출한, 신뢰할 수 없는 외부 URL을 서버가 직접 요청하는 **첫 지점**이라 이 프로젝트에서 가장 공격 표면이 넓은 위험 로직이다.

## 결정 1 — SSRF 방어 깊이

**결정: DNS 해석 후 IP 검증 + redirect마다 재검증.**

| 선택지 | 트레이드오프 |
|---|---|
| **DNS 해석 → IP 검증 → redirect마다 재검증** (채택) | hostname을 IP로 먼저 해석해 사설(RFC1918)·loopback·link-local·클라우드 메타데이터(`169.254.169.254`) 대역을 차단하고, redirect가 일어날 때마다 같은 검증을 반복한다. DNS rebinding(검증 시점과 연결 시점 사이 DNS 응답이 바뀌는 공격)까지 막는다. 대신 HttpClient5에 커스텀 DNS resolver·redirect handler가 필요해 구현이 더 복잡하다 |
| 최초 hostname/IP만 검증, redirect는 scheme만 재확인 | 구현은 단순하지만 redirect 체인 중간에 내부 IP로 우회하는 고전적 SSRF 공격과 DNS rebinding을 막지 못한다 |

**핵심 이유**: [기술스택.md](기술스택.md)가 Apache HttpClient5를 택한 이유 자체가 "redirect마다 host + resolved IP 재검증이 자연스럽게 표현됨"이었다 — 이 결정은 그 전제를 실제로 채우는 것이다. SSRF는 이 프로젝트의 본질적 위험(cs-learning D축 P0)이라 얕은 방어로 타협하지 않는다.

## 결정 2 — Timeout 예산 구조

**결정: connect(3s) / 응답(TTFB, 5s) / 읽기 전체(15s) 3구간 분리.**

| 선택지 | 트레이드오프 |
|---|---|
| **구간별 분리** (채택) | 어느 구간에서 실패했는지(연결 자체 실패 vs 서버가 느림 vs 전송이 느림) 구분 가능 — 장애 유형별 대응·재시도 판단의 근거가 된다 |
| 단일 총 timeout | 구현은 단순하지만 실패 원인을 구분 못 해 운영 중 장애 분석이 어렵다 |

**핵심 이유**: cs-learning D축이 "connect/response/read timeout 차이"를 명시적 학습 목표로 요구하고, 구간별 실패 원인이 이후 재시도 정책(결정 3)의 입력이 된다.

## 결정 3 — 재시도 정책과 본문 크기 상한

**결정: timeout·429·일부 5xx만 최대 2회 제한 재시도(exponential backoff + jitter). 4xx·형식 오류는 즉시 종료. 본문은 압축 해제 후 5MB 상한.**

| 선택지 | 트레이드오프 |
|---|---|
| **제한 재시도 + 크기 상한** (채택) | 일시적 네트워크 문제로 인한 불필요한 실패를 줄이면서도, 재시도 폭주(같은 장애 도메인에 요청 집중)와 무제한 본문으로 인한 메모리 문제를 동시에 막는다 |
| 재시도 없음 / 크기 제한 없음 | 구현은 가장 단순하지만 일시적 오류가 바로 영구 실패로 기록되고, 큰 문서가 heap을 압박할 위험이 그대로 남는다 |

**핵심 이유**: 4xx·형식 오류는 재시도해도 결과가 달라지지 않는 오류라 재시도 예산을 낭비하지 않는다. 크기 상한은 이 프로젝트가 아직 스트리밍 처리 인프라를 갖추지 않았다는 전제([기술스택.md](기술스택.md) HttpClient5 트레이드오프)와도 일치한다.

## 결정 4 — 동일 Link에 대한 중복 fetch 시도 방지

**결정: `link.status`를 원자적 조건부 UPDATE로 선점(claim)한다.** `UPDATE link SET status='FETCHING' WHERE id=? AND status='PENDING' RETURNING id` — 영향받은 row가 0개면 이미 다른 호출이 처리 중이므로 즉시 반환(no-op)한다.

| 선택지 | 트레이드오프 |
|---|---|
| **상태 컬럼 원자적 선점(claim)** (채택) | 새 테이블 없이 `link.status`에 `FETCHING` 한 값만 추가하면 됨. plan-01(refresh token consume)·plan-02(link upsert)에서 이미 쓴 "조건부 UPDATE로 원자성 확보" 패턴과 동일해 하네스 전체가 일관됨 |
| 별도 `fetch_attempt`(멱등) 테이블 신설 | `architecture/async-pipeline.md`가 언급한 `inputHash`/`jobId` 방식과 더 정교하게 맞지만, 그건 plan-04의 job 테이블·worker 인프라와 함께 설계돼야 할 것 — 지금 만들면 plan-04 설계와 겹치거나 어긋날 위험 |
| 지금 처리 안 함(plan-04로 미룸) | 실제 동시 호출자가 아직 없어 당장은 안전하지만, plan-04가 이 전제를 놓치면 조용히 이중 fetch가 발생 — "나중에 잠금을 추가하겠지"라는 암묵적 가정에 기대는 나쁜 기본값 |

**핵심 이유**: 이 문제는 plan-01의 refresh token 소비 race, plan-02의 동시 저장 race와 본질적으로 같은 모양이다(이미 존재하는 row를 조건부로 한 번만 전이시키는 문제) — 같은 해법을 재사용하는 게 새 개념을 도입하는 것보다 낫다. `fetch_attempt` 테이블 같은 더 정교한 멱등 장치는 plan-04에서 job 인프라 전체를 설계할 때 필요하면 다시 검토한다.

## 트레이드오프 종합

- 결정 1의 redirect 재검증은 매 hop마다 DNS 조회가 추가돼 지연이 소폭 늘지만, SSRF는 이 프로젝트에서 타협 불가 영역이라 감수한다.
- 결정 3의 재시도 상한(2회)과 크기 상한(5MB)은 초기 가설이다 — 실제 실패 URL 표본에서 근거 없이 잘리는 정상 문서나 재시도로 구제되는 일시 오류 비율이 관측되면 조정한다.

## 재검토 조건

- 실제 운영에서 5MB 상한 때문에 정상 문서가 반복적으로 잘리면 상한을 올리거나 스트리밍 처리를 재검토한다.
- SSRF 방어가 실제 우회 사례로 뚫리면(보안 사고) 즉시 이 ADR을 재검토하고 방어 계층을 추가한다.
- 재시도 2회로도 일시 오류 복구율이 낮게 관측되면 backoff 정책·횟수를 조정한다.

## 면접 답변 요지

> "SSRF는 URL만 검증하는 게 아니라 DNS 해석 결과(IP)를 검증하고, redirect가 일어날 때마다 그 검증을 반복해 DNS rebinding까지 막았다. timeout은 연결·응답·읽기 3구간으로 나눠 장애 유형을 구분할 수 있게 했고, 재시도는 재시도해도 의미 없는 4xx는 제외하고 timeout·429·5xx만 제한적으로, backoff+jitter로 재시도 폭주를 막았다. 본문 크기 상한은 스트리밍 인프라가 없는 현재 스택의 제약을 인정하고 무제한 처리 대신 명시적 상한을 선택한 것이다."
