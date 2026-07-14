# 조건부 기술 도입 판정

> 출처: `learning/cs-learning.md`에서 분리 (0절 분류표, 2-E 3단계 진화, 4절 판정표)
> 원칙: Kafka·Redis·SSE·Qdrant는 "쓰기 위해" 도입하지 않는다. **필요 신호가 측정되면** 도입하고, 그 전까지는 실험 branch·ADR로만 남긴다.
> 초기 판정값은 절대 진리가 아니라 **실험 전에 고정하는 가설**이다.

## 1. 지금 보류한 기술과 판단

| 항목 | 판단 | 이유 |
|---|---|---|
| Kafka | P1 단계적 진화 | 비동기 처리는 필수지만 Kafka는 필수가 아니다. Job polling→Outbox publisher→Debezium CDC+Kafka 순서로 진화하며 전환 신호를 측정한다. |
| 로컬/Redis 캐시 | P1 비용용·P2 성능용 | LLM/embedding 중복 호출 방지는 호출 단가로 판단하고, 조회 성능 캐시는 hit ratio와 병목을 확인한 뒤 적용한다. |
| JVM/JFR/GC | P2 진단 후 적용 | 대량 재색인·큰 문서 처리에서 메모리 문제가 관측될 때만 깊게 분석한다. GC 옵션 변경 자체는 목표가 아니다. |
| tcpdump | LAB 또는 장애 발생 시 | 외부 호출 장애가 애플리케이션 로그·메트릭만으로 구분되지 않을 때 패킷 분석으로 내려간다. |
| Netty SSE | MVP 제외 | 처리 상태는 polling으로 충분하다. 동시 실시간 연결이라는 사용자 요구가 생길 때만 검토한다. |
| Tomcat→Netty 전환 | 제외 | 스레드 모델 교체가 LinkPocket의 사용자 문제를 해결하지 않으며 비교 변수만 늘린다. |

## 2. 비동기: Job polling → Outbox → Kafka CDC 3단계 진화

| 단계 | 구조 | 이 단계가 적합한 조건 | 다음 단계로 넘어갈 신호 |
|---|---|---|---|
| 1. Job polling | 비즈니스 transaction에서 `job` row를 함께 저장하고 worker가 `SKIP LOCKED`로 직접 claim | 단일 pipeline, 소비 주체 1개, DB 보존 범위의 재처리로 충분 | 명령 작업과 도메인 event 책임이 섞임, 여러 후속 처리에 발행 보장이 필요, polling lock·pool wait로 queue SLO 반복 위반 |
| 2. Transactional Outbox + publisher | 비즈니스 변경과 outbox event를 한 transaction에 기록하고 별도 publisher가 batch 발행 | DB commit과 event 발행 의도를 원자적으로 보존해야 하지만 app publisher로 처리량 충족 | target workload에서 index/batch 튜닝 후에도 `outbox_publish_lag`가 SLO를 반복 위반하거나 publisher 장애·재배포가 병목 |
| 3. Debezium CDC + Kafka | DB WAL/binlog를 Debezium이 읽어 Kafka에 발행하고 consumer group별 offset·replay 운영 | 독립 consumer·장기 replay·partition 순서·대규모 backlog 처리가 제품 요구 | 운영 복잡도까지 감당할 가치가 없으면 2단계 유지 |

우아한형제들 사례는 MySQL Source Connector와 binlog를 사용하지만 LinkPocket의 운영 DB는 PostgreSQL이다. 따라서 그대로 복제하지 않고 Debezium PostgreSQL Connector, logical replication, replication slot, WAL 보존량과 장애 복구를 별도로 검증한다.

**전환 신호 초기 가설:**
- target arrival rate에서 `oldest_job_age p95 ≤ 30초`를 queue SLO 초안으로 둔다.
- 2단계에서 `outbox_publish_lag p95 > 10초`가 10분 이상 지속되고 query·index·batch 개선 후에도 반복되면 CDC를 검토한다.
- 서로 다른 시점에서 replay해야 하는 독립 consumer가 3개 이상이거나 DB 보존 기간을 넘는 event replay가 필요하면 Kafka 필요조건이 성립한 것으로 본다.
- worker/publisher를 늘렸는데 처리량보다 DB lock wait·pool pending이 먼저 증가하면 DB polling의 확장 한계를 기록한다.

## 3. 조건부 기술의 도입 판정표

| 단계/기술 | 전환·도입을 검토할 신호 | 먼저 비교할 대안 | 결과가 없으면 |
|---|---|---|---|
| Job polling→Outbox | 여러 후속 처리에 발행 보장이 필요하거나 queue SLO가 lock/pool 병목으로 반복 위반 | job 상태·멱등성·index·batch·worker 조정 | Job polling 유지 |
| Outbox publisher→Debezium CDC | publisher 튜닝 후에도 publish lag SLO 위반, 장애 복구/순서 요구 증가 | publisher batch·partitioning·재시도 개선 | Outbox publisher 유지 |
| Kafka consumer 운영 | 독립 replay consumer 3개 이상, DB 보존 밖 replay, partition 순서·대량 backlog 필요 | 단일 pipeline worker와 재색인 command | 실험 branch와 ADR만 남기고 운영 미도입 |
| 비용용 cache | AI 호출 절감액이 저장·검증·stale 비용보다 큼 | content hash dedup, provider cached input, batch | 결과 cache 미도입 |
| 성능용 Redis/local cache | 반복 key의 hit ratio와 DB/Qdrant 병목이 측정됨 | 쿼리·인덱스 개선, client cache | 미도입 또는 rate limit만 사용 |
| 분산 rate limiter | 인스턴스 2개 이상에서 user quota를 일관되게 적용하거나 abuse 한도가 보안 경계가 됨 | 단일 인스턴스 local limiter+gateway limit | Redis Lua/원자 연산 미도입 |
| Playwright/headless browser | 최근 실패 표본 100개 중 동적 렌더링 필요가 10% 이상이거나 핵심 대상 domain이 반복 실패 | OG/JSON-LD·정적 embedded data·원문 링크만 저장 | 정적 fetch 유지, unsupported 상태 명시 |
| `@Scheduled`→ShedLock | 애플리케이션 인스턴스가 2개 이상이며 같은 digest 동시 실행 가능 | digest run unique key와 멱등성 | 단일 인스턴스 `@Scheduled` 유지 |
| ShedLock→durable scheduler | 사용자별 예약, misfire 보장, 장기 job, 운영 UI·수동 재실행 필요 | DB job table·outbox·운영 CLI | ShedLock+job table 유지 |
| GC option tuning | GC pause/CPU가 실제 SLO 위반의 주원인 | streaming, batch 축소, unbounded collection 제거 | 기본 G1과 명시적 heap limit 유지 |
| JVM/DB warm-up | 배포 직후와 Neon suspend 후 지연을 분리 측정해 JVM class loading/JIT가 SLO 위반 원인으로 확인 | startup probe, lazy path 제거, DB reconnect·query 개선 | 인위적 warm-up 미도입 |
| tcpdump | app log/metric에서 timeout과 reset·retransmission을 구분 불가 | structured error, HTTP client metric, Toxiproxy | LAB만 수행하거나 생략 |
| SSE/Netty | 사용자가 진행 상태를 실시간으로 봐야 하고 polling 비용·지연이 문제 | polling, 짧은 long-poll | 미도입 |

Playwright를 도입할 경우 RSS·CPU·페이지 처리시간을 현재 배포 환경에서 직접 측정한다. 고정된 "브라우저 1개당 메모리" 수치를 전제하지 않으며, 신뢰할 수 없는 JavaScript 실행은 main application과 분리된 sandbox worker, URL 재검증, resource/domain/시간/크기 제한 아래에서만 허용한다.

## 4. 기각 기록도 증거다

기술을 기각한 기록은 다음 조건을 만족하면 좋은 포트폴리오 증거다.

- 어떤 문제를 예상했는가
- baseline은 무엇이었는가
- 후보 기술이 어떤 지표를 개선해야 했는가
- 개선 폭과 운영 복잡도를 어떻게 비교했는가
- 어떤 조건이 되면 결정을 다시 열 것인가

> 관련: 8주차 심화 실험 후보 선정은 [learning/cs-learning.md](../learning/cs-learning.md) 5절, 실험 실행은 [experiments/](../experiments/) 참고.
