# 비동기 파이프라인·상태 머신·처리 상태 노출

> 출처: `learning/cs-learning.md`에서 분리 (2-E 상태 노출 정책·scheduler)
> 전제: 저장 API는 링크·작업을 기록하고 빠르게 응답, fetch→parse→embed→index는 background. 비동기 구조 진화(Job polling→Outbox→Kafka)는 [decisions/conditional-tech-adoption.md](../decisions/conditional-tech-adoption.md) 참고.

## 1. 상태 머신 (내부)

```text
PENDING → FETCHED → CHUNKED → INDEXED
(+ 실패·중단·재처리 상태와 허용 전이 정의)
```

- at-least-once + 멱등성: `linkId/jobId/inputHash/parserVersion/modelVersion`으로 중복 실행 결과를 고정한다.
- transaction 경계: 링크 저장과 최초 작업 생성은 같은 transaction, HTTP/LLM 호출은 DB transaction 밖.
- 재시도·DLQ보다 오류 분류: retryable/non-retryable 구분, 최대 횟수와 다음 실행 시각, 수동 재처리.
- queue·worker capacity: worker 수, HTTP/DB connection, LLM rate limit을 함께 제한한다.

## 2. 처리 상태의 사용자 노출 정책

- API는 내부 단계 전체를 노출하지 않고 `QUEUED`, `PROCESSING`, `READY`, `FAILED`처럼 사용자 행동에 필요한 상태만 제공한다.
- **처리 상태 확인은 polling** (SSE 아님 — [decisions/conditional-tech-adoption.md](../decisions/conditional-tech-adoption.md)). polling 초안은 `1초→2초→4초→8초`, 최대 10초에 jitter를 적용하고 terminal 상태에서 즉시 중단한다. 서버가 `Retry-After`를 반환하면 그 값을 우선한다.
- 20초를 넘으면 "처리는 계속 진행 중이며 다른 작업을 해도 된다"는 안내와 나중에 확인/알림 선택지를 제공한다.
- `FAILED`에는 안전한 재시도, 원문만 저장, 삭제 중 가능한 사용자 행동을 오류 유형별로 제공한다.
- ETag/상태 version을 사용해 변경 없는 polling 응답의 payload를 줄이고, k6에서 완료 인지 지연과 polling RPS를 함께 측정한다.

## 3. 주간 다이제스트 scheduler·메일 발송

| 항목 | LinkPocket 적용과 선택 기준 |
|---|---|
| `@Scheduled` vs durable scheduler | 단일 인스턴스·고정 월요일 8시는 `@Scheduled`로 시작, 사용자별 예약·misfire·수동 재실행 요구가 커지면 Quartz/JobRunr/db-scheduler 검토 |
| 다중 인스턴스 중복 실행 | 인스턴스가 2개 이상일 때 JDBC ShedLock 등으로 동시 실행만 방지 |
| batch idempotency | `(digestWeek, userId, templateVersion)` unique key와 발송 상태 머신 |
| at-least-once delivery | 대상 선정과 메일 발송을 한 transaction으로 묶지 않고 send job·provider message ID 저장 |
| bounce·complaint·unsubscribe | webhook을 서명 검증하고 suppression/preferences에 반영 |
| timezone·DST | 사용자 timezone 기준 주간 window와 UTC 저장 |

- ShedLock은 distributed scheduler가 아니라 **동시에 한 번만 실행되도록 돕는 lock**이다. 실행 보존·misfire·재시도까지 필요하면 별도 scheduler/작업 테이블을 사용한다. (도입 판정: [decisions/conditional-tech-adoption.md](../decisions/conditional-tech-adoption.md))
- 이메일 본문에 인증 없이 사용할 수 있는 서명된 unsubscribe 링크와 preference center를 제공하되 token은 목적·사용자·만료에 묶고 원문을 로그에 남기지 않는다.
- 다이제스트의 광고성/서비스성 구분, 동의와 수신거부 표시는 공개 전에 최신 법령·KISA 안내와 실제 메일 내용 기준으로 별도 검토한다.
- bounce·complaint webhook은 외부 입력이므로 replay·위조·중복을 검증하고, provider 장애 시 무한 재발송하지 않는다.
