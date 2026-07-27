---
기능: 비동기 AI 파이프라인 — job polling·요약·임베딩·pgvector 색인·상태머신
관련 축: E. 비동기 처리·job queue / F. 검색·요약 품질 / 주차: 3
상태: 초안
---

## 목적 (한 줄)
fetch가 끝난 Link를 백그라운드에서 요약·청킹·임베딩·색인해, 저장 후 1분 이내 검색 가능하게 만든다 — 이 과정에서 어떤 장애가 나도(AI API·worker 죽음) 처리가 영구히 누락되지 않고, 같은 job이 재시도돼도 LLM을 중복 호출하지 않는다.

## 범위
- **포함:** DB job 테이블 없이 `link.status` 기반 claim(ADR-011 결정 4 패턴 확장) + `SKIP LOCKED` polling worker, plan-03의 `LinkFetchService.fetchAndExtract`를 실제로 트리거하는 지점, 요약(`gpt-4o-mini`)·청킹(구조 보존+fallback+overlap, [ADR-013](../decisions/adr-013-chunking-strategy.md))·임베딩(`text-embedding-3-small`)·pgvector 색인, 상태머신 확장, 클라이언트 상태 polling용 공개 API(`GET /api/links/{id}/status` 또는 기존 `lookup` 확장).
- **제외:** 카테고리 자동 분류(plan-05), 검색 API 자체(plan-06), 이미지 내 텍스트(OCR) 처리(MVP2, [ADR-013](../decisions/adr-013-chunking-strategy.md)) — 이 plan은 "색인까지"가 끝이고 검색은 다음 plan이 읽는다.
- **위험 로직·기술 선택 전부 합의 완료** — job claim·tx 경계·멱등성은 [ADR-012](../decisions/adr-012-async-pipeline-job-claim-and-idempotency.md), chunking 전략은 [ADR-013](../decisions/adr-013-chunking-strategy.md), AI 처리 실패 노출·재시도는 [ADR-014](../decisions/adr-014-ai-processing-failure-exposure-and-retry.md).

## Acceptance Criteria (수용 기준)
- [ ] `FETCHED` 상태의 Link는 worker가 자동으로 집어가 요약→청킹→임베딩→색인을 순서대로 수행하고, 모두 성공하면 상태가 `INDEXED`로 전이한다.
- [ ] 저장(`POST /api/links`) 완료 후 1분 이내(P95 기준, [ADR-012](../decisions/adr-012-async-pipeline-job-claim-and-idempotency.md) 관찰 지표) 파이프라인 전체가 끝나 `INDEXED` 상태에 도달한다(정상 케이스).
- [ ] worker 여러 개가 동시에 polling해도 같은 Link를 두 worker가 동시에 처리하지 않는다(`SKIP LOCKED` 배타적 claim).
- [ ] claim 이후 worker가 죽거나 AI API가 무응답이어도, `lease_expires_at` 초과 시 다른 worker가 자동으로 재claim해 처리를 이어간다.
- [ ] 같은 Link에 대해 요약/임베딩이 두 번 실행돼도(재claim으로 인한 재시도 포함), 이미 결과가 있으면 LLM을 다시 호출하지 않고 기존 결과를 재사용한다 — LLM 호출 횟수는 고유 job당 최대 1회로 수렴한다.
- [ ] 재시도 불가 사유(콘텐츠 정책 거부 등)는 1회 시도로 즉시 영구 실패 확정, 재시도 가능 사유(API 일시 장애 등)는 최대 2회까지 재시도 후 실패 확정한다([ADR-014](../decisions/adr-014-ai-processing-failure-exposure-and-retry.md)).
- [ ] 요약·임베딩 단계에서 실패(재시도 소진 포함)해도 Link row·URL·제목·추출된 본문은 삭제되지 않고, 목록에서 정상 노출된다 — 요약 필드에만 "요약 생성 실패" 메시지가 채워진다.
- [ ] AI 처리가 영구 실패한 Link는 검색(색인)에서만 제외되고 목록·상세 열람은 그대로 가능하다.
- [ ] 클라이언트는 상태 polling API로 `QUEUED`/`PROCESSING`/`READY`/`READY_WITHOUT_CONTENT`/`FAILED` 중 하나만 받는다(내부 세부 상태는 노출 안 함, [architecture/async-pipeline.md](../architecture/async-pipeline.md) §2). 사용자에게 수동 재시도 UI는 제공하지 않는다.

## 불변식 (항상 참이어야 하는 것)
- 같은 `(linkId, inputHash, modelVersion)`에 대해 요약/임베딩 결과 row는 항상 최대 1개다(unique constraint).
- `PROCESSING`류 내부 상태(예: `SUMMARIZING`)는 항상 `lease_expires_at`을 동반한다 — lease 없는 processing 상태는 없다.
- LLM/HTTP 호출은 어떤 단계에서도 DB 트랜잭션 안에서 일어나지 않는다(ADR-012 결정 2).
- AI 처리(요약·청킹·임베딩) 실패가 Link 삭제나 URL·제목·본문 유실로 이어지지 않는다([invariants.md](../product/invariants.md)).
- 서버가 세션에서 뽑은 `userId` 소유의 Link만 이 파이프라인이 처리·조회한다(tenant 격리, invariants.md 전역 불변조건).

## 실패 조건 (이렇게 되면 실패로 본다)
- 두 worker가 같은 Link를 동시에 요약/임베딩하면 실패(claim 배타성 위반).
- lease가 만료된 job이 일정 시간(관찰 지표 기준) 내에 재claim되지 않고 방치되면 실패(가용성 위반).
- 같은 job의 재시도로 LLM이 2회 이상 호출되면 실패(비용 위반, 호출 전 조회 가드 누락).
- AI 처리 실패로 Link row나 이미 추출된 본문이 사라지면 실패.
- 상태 polling API가 내부 세부 상태(`SUMMARIZING` 등)를 그대로 노출하면 실패(캡슐화 위반).
- 남의 tenant의 Link가 처리되거나 상태 조회에 노출되면 실패.

## 위험 로직 결정 (합의 완료)
- Job claim 동시성: `SELECT ... FOR UPDATE SKIP LOCKED` 배타 claim, 브로커 없이 DB만 → [ADR-012](../decisions/adr-012-async-pipeline-job-claim-and-idempotency.md) 결정 1
- 트랜잭션 경계·worker 장애 복구: LLM/HTTP 호출은 tx 밖, `lease_expires_at` 기반 복구 스캔 → ADR-012 결정 2
- 멱등성(비용 방지): 호출 전 `(linkId, inputHash, modelVersion)` 존재 조회 + unique constraint 백스톱 → ADR-012 결정 3
- Chunking 전략: 구조 보존 우선 + 고정 크기 fallback + overlap → [ADR-013](../decisions/adr-013-chunking-strategy.md)
- AI 처리 실패 노출·재시도: 목록엔 유지하고 요약란에 실패 메시지, 재시도 가능 사유만 최대 2회 → [ADR-014](../decisions/adr-014-ai-processing-failure-exposure-and-retry.md)

## 상태 머신

```
PENDING → FETCHING → FETCHED → SUMMARIZING → SUMMARIZED → CHUNKING → CHUNKED → EMBEDDING → INDEXED
                    ↘ READY_WITHOUT_CONTENT (본문 추출 실패, plan-03)
       (모든 단계) ↘ FAILED (재시도 불가 fetch 실패, plan-03)
   (SUMMARIZING 이후 임의 단계, 재시도 소진) ↘ READY_WITHOUT_INDEX (AI 처리 영구 실패 — 본문·제목은 보존, 요약란만 "요약 생성 실패")
```

- `*ING` 상태는 모두 `link.processing_lease_expires_at`을 동반하는 claim 상태다.
- `SUMMARIZING`/`CHUNKING`/`EMBEDDING`은 각 단계 진입 시 `link.processing_attempt_count`를 증가시킨다 — 재시도 가능 사유로 실패해 재claim될 때마다 누적되고, 2를 넘으면 재시도 없이 `READY_WITHOUT_INDEX`로 확정한다([ADR-014](../decisions/adr-014-ai-processing-failure-exposure-and-retry.md)). 재시도 불가 사유는 카운트와 무관하게 즉시 `READY_WITHOUT_INDEX`로 전이한다.
- 클라이언트 노출 매핑: `PENDING/FETCHING` → `QUEUED`, `SUMMARIZING/CHUNKING/EMBEDDING` → `PROCESSING`, `INDEXED` → `READY`, `READY_WITHOUT_CONTENT`/`READY_WITHOUT_INDEX` → `READY_WITHOUT_CONTENT`(둘 다 "링크 자체는 살아있고 목록엔 보이지만 검색엔 안 걸림"으로 통일), `FAILED` → `FAILED`.

## 에러 코드 계약 — 내부 실패 사유 확장

`FetchFailureReason`(plan-03)과 별도로 AI 처리 실패 사유를 정의한다. 공개 API는 여전히 위 5개 상태만 노출하고, 사유는 로그·관측성 용도다. "재시도 가능"은 [ADR-014](../decisions/adr-014-ai-processing-failure-exposure-and-retry.md) 기준 — 가능 사유는 `processing_attempt_count`가 2를 넘을 때까지 재시도, 불가 사유는 1회 만에 즉시 `READY_WITHOUT_INDEX` 확정.

| 사유 코드 | 의미 | 재시도 가능 여부 |
|---|---|---|
| `SUMMARIZE_API_ERROR` | OpenAI API 호출 실패(5xx/timeout) | 예(최대 2회, lease 만료로 재claim) |
| `SUMMARIZE_CONTENT_REJECTED` | 콘텐츠 정책 거부 등(4xx류) | 아니오(즉시 확정) |
| `EMBEDDING_API_ERROR` | 임베딩 API 호출 실패(5xx/timeout) | 예(최대 2회) |
| `EMBEDDING_CONTENT_REJECTED` | 임베딩 API가 4xx로 거부(콘텐츠 정책 등) | 아니오(즉시 확정) |
| `CHUNKING_FAILED` | 청킹 단계 내부 오류(빈 본문 등) | 아니오(즉시 확정) |
| `INDEX_WRITE_FAILED` | pgvector upsert 실패 | 예(최대 2회) |

## API 계약

### `GET /api/links/{linkId}/status` (신규)
- 인증 필요, 소유자만 조회 가능(IDOR 방어 — 타 tenant면 404로 통일, api-error-contract.md 원칙).
- 응답: `{ "linkId": "...", "status": "PROCESSING", "updatedAt": "..." }` — `status`는 위 5개 값 중 하나만.

| 코드 | HTTP status | 화면 처리 | 사용자 문구 owner |
|---|---|---|---|
| `LINK_NOT_FOUND` | 404 | 토스트 | BE 기본값 |

## 구현 계획 (AI가 따를 단계) — task 분해

1. **실패 테스트 먼저(계약 테스트, Claude)** — job claim 배타성(동시 100회 claim 시도 시 1개만 성공), lease 만료 후 재claim, 호출 전 조회로 중복 LLM 호출 안 남, 재시도 불가 사유는 1회 만에 즉시 확정, 재시도 가능 사유는 2회까지만 재시도 후 확정, AI 실패해도 Link·본문 보존 + 목록 노출 + 요약란 실패 메시지, tenant 격리, 상태 API가 내부 상태 안 새어나감.
2. Flyway 마이그레이션 — `link`에 `processing_lease_expires_at timestamptz`, `processing_attempt_count int default 0` 추가, 요약/청크/임베딩 결과 테이블(`link_summary`, `link_chunk` 등, `(link_id, input_hash, model_version)` unique) 신설. `idle_in_transaction_session_timeout` 설정 검토.
3. **`LinkProcessingWorker`** — `@Scheduled` polling loop, `SKIP LOCKED`로 배치 claim(PENDING/FETCHED/SUMMARIZED/CHUNKED 중 하나이거나 lease 만료된 `*ING`), 현재 상태에 맞는 다음 단계 dispatch.
4. plan-03 `LinkFetchService.fetchAndExtract` 트리거를 이 worker의 `PENDING→FETCHING` claim 단계에 연결(현재 서비스는 존재하나 아무도 안 부르는 상태 — 이번에 배선).
5. `SummarizeService`/`EmbeddingService` — 호출 전 결과 존재 조회 → 없으면 OpenAI 호출(tx 밖) → 결과 저장(tx 안, unique constraint). 실패 시 사유를 재시도 가능/불가로 분류해 `processing_attempt_count` 증가 또는 즉시 `READY_WITHOUT_INDEX` 확정.
6. 청킹 구현 — [ADR-013](../decisions/adr-013-chunking-strategy.md) 채택안(구조 보존 우선 + 고정 크기 fallback + overlap)대로 구현.
7. `GET /api/links/{linkId}/status` 엔드포인트 + 내부→외부 상태 매핑.

<AI가 제안한 계획이다 — 사람이 대조·수정한다.>

## 대조 기록 (SDD 증거)
- AI 계획 중 수정/거절한 것과 이유:
- spec↔구현 누락 건수 · 수정 turn 수:
- job claim 동시성 테스트(동시 N회 claim) 결과:
