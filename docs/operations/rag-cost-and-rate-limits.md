# LLM 비용·rate limit·cache 운영 정책

> 출처: `learning/cs-learning.md`에서 분리 (2-F LLM rate limit·비용·cache, 2-E 자체 API rate limiting)

## 1. LLM provider rate limit·비용 예산 운영

| 상황 | 기본 정책 | 사용자 경험 |
|---|---|---|
| 429 | `Retry-After` 우선, 없으면 exponential backoff+jitter, retry budget 내 재시도 | job을 `RETRY_SCHEDULED`로 두고 저장 자체는 성공 |
| provider 5xx/timeout | 짧은 제한 재시도 후 circuit open, 다른 요청의 retry 폭주 차단 | 기존 summary/keyword search 제공, 생성은 지연 |
| 일반 4xx | 자동 재시도하지 않고 schema·권한·입력 문제로 분류 | 수정 가능한 입력이면 안내, 아니면 원문만 저장 |
| 월 예산 70% | 알람과 일별 비용 예측 갱신 | 변화 없음 |
| 월 예산 90% | 비핵심 자동 요약/digest 축소, 저비용 모델·batch 검토 | 핵심 검색·원문 저장 유지 |
| 월 예산 100% | 관리자가 명시적으로 해제하기 전 고비용 generation 중단 | keyword/hybrid search와 기존 결과로 fallback |

70/90/100%는 최초 운영 가설이며 사용자 수와 실제 단가를 보고 ADR에서 조정한다. token bucket/semaphore는 model별 RPM·TPM과 평균 token을 기준으로 잡고, HTTP worker 수와 별도로 제한한다.

**token·비용 회계:** 기능·사용자·모델별 input/output/cache token과 원화 환산 비용을 generation ID에 기록한다. cost/link, cost/query, 월 누적·예측 비용, 예산 초과 횟수를 본다.

## 2. 성능용 cache와 비용용 cache 분리

| 목적 | 후보 | 도입 기준 | key·무효화 핵심 |
|---|---|---|---|
| 비용 절감 | summary·embedding 결과 | hit ratio가 낮아도 `절감 호출 비용 > 저장·정합성 비용`이면 도입 | `contentHash+modelVersion+promptVersion`; URL만 key로 쓰지 않음 |
| 비용 절감 | query embedding | 반복 질의의 embedding 비용·latency가 의미 있을 때 | normalized query+embedding model version, 개인정보가 포함된 query의 공유 cache 금지 |
| 성능 개선 | archive/search 결과 | DB/Qdrant 병목과 반복 조회가 측정될 때 | user/filters/indexVersion, 짧은 TTL·삭제 전파 |
| 계산 절감 | canonicalization/parser metadata | profiling에서 반복 계산이 보일 때 bounded local cache | canonicalization rule/parser version |

같은 URL도 본문이 바뀔 수 있으므로 ETag/Last-Modified conditional fetch 또는 재검증 주기 없이 과거 summary를 영구 재사용하지 않는다.

## 3. 자체 API rate limiting

| 대상 | key와 알고리즘 초안 | 초과 시 동작 | 검증 지표 |
|---|---|---|---|
| 로그인·magic link 요청 | IP+정규화 계정의 sliding window, 점진적 지연 | 계정 존재 여부를 숨긴 동일 응답, `Retry-After`, 보안 event | 성공/실패율, false lockout, 공격 IP 분포 |
| 링크 저장 | user+route token bucket, 비로그인은 IP 보조 한도 | 429, 기존 작업 상태 반환, 같은 URL 중복은 멱등 처리 | 허용 burst, queue 유입, 정상 사용자 차단률 |
| 검색 | user token bucket | 429와 재시도 시점 | p95, Qdrant 부하, 사용자별 burst |
| RAG generation | user+model quota와 동시 실행 semaphore | keyword/hybrid search fallback | token cost, 429, queue wait |
| OAuth callback/refresh | session/token family 단위 replay 제한 | family 폐기 또는 재인증 | reuse detection과 공격 재현 |

- 단일 인스턴스 alpha는 in-memory limiter로 시작할 수 있지만 재시작·다중 인스턴스에서 정확도가 필요하면 Redis로 이동한다. (도입 판정: [decisions/conditional-tech-adoption.md](../decisions/conditional-tech-adoption.md))
- Redis 구현은 `INCR+EXPIRE` fixed window의 경계 burst를 이해하고, sliding window/token bucket은 Lua 또는 검증된 원자 연산으로 처리한다.
- IP만으로 사용자를 식별하지 않는다. NAT·프록시·IPv6를 고려하고 인증 후에는 user를 주 key, IP는 abuse signal로 사용한다.
- rate limit은 권한 검사를 대체하지 않으며 한도·거부·backend 장애 시 fail-open/closed 정책을 endpoint 위험도별 ADR로 남긴다.

## 4. AI dependency fallback

AI 장애 시 링크 저장·원문 조회·keyword search는 유지한다. 모델/벡터 DB 장애를 주입해 핵심 기능 생존 여부를 검증한다. (fault injection 실험: [experiments/](../experiments/))
