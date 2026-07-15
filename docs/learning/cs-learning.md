# LinkPocket CS 학습·적용 로드맵

> 재작성일: 2026-07-12 (문서 분리: 2026-07-13)
> 전제: Java/Spring 기반 개인 프로젝트, 하루 6시간, 출시 후 1~2개월 운영
> 목표: 백엔드 기본기와 AI 활용 역량을 **LinkPocket의 실제 문제에 필요한 만큼 깊게 적용**한다.
>
> 이 문서는 **"어느 주차에 무엇을 만들며 무엇을 학습하는가"**의 중점만 담는다.
> 세부 구현·운영 정책은 각 폴더로 분리했다 — 아래 표의 포인터를 따라가면 된다.

## 분리된 상세 문서

| 원래 내용 | 이동한 곳 |
|---|---|
| Modular Monolith 논거 | [decisions/adr-001-modular-monolith.md](../decisions/adr-001-modular-monolith.md) |
| Kafka/Redis/SSE 등 조건부 기술 판정 | [decisions/conditional-tech-adoption.md](../decisions/conditional-tech-adoption.md) |
| AI 한계 6종 실험 | [experiments/ai-limits-experiments.md](../experiments/ai-limits-experiments.md) |
| 개인정보·이용자 권리·크롤러 정책 | [operations/data-privacy-and-rights.md](../operations/data-privacy-and-rights.md) |
| OTel·SLO·제품 KPI·검색/요약 CI | [operations/observability-slo-kpi.md](../operations/observability-slo-kpi.md) |
| LLM 비용·cache·rate limit | [operations/ai-cost-and-rate-limits.md](../operations/ai-cost-and-rate-limits.md) |
| 인증·세션 경계 | [architecture/auth-and-session.md](../architecture/auth-and-session.md) |
| 비동기 파이프라인·상태 노출·scheduler | [architecture/async-pipeline.md](../architecture/async-pipeline.md) |
| DB 모델·Neon 제약 | [architecture/database-and-infra.md](../architecture/database-and-infra.md) |
| 채용 신호·산출물·서사 | [reference/career-narratives.md](../reference/career-narratives.md) |
| 자료 조사 링크 | [reference/sources.md](../reference/sources.md) |
| 출시 전 최종 체크리스트 | [../release-checklist.md](../release-checklist.md) |

---

## 1. 학습 우선순위 (8축)

LinkPocket의 핵심 흐름을 먼저 놓고 후보를 선별했다.

```text
외부 URL 저장
→ 안전하게 본문 수집
→ 비동기 전처리·임베딩·색인
→ 시맨틱 검색·연관 추천
→ 실제 사용자 운영과 개선
```

이 흐름에서 자연스럽고 채용 증거로도 강한 여덟 축이다.

| 우선순위 | 학습·적용 축 | 선정 이유 |
|---|---|---|
| P0 | 명세·계약과 TDD 기반 테스트 자동화 | 신입 백엔드의 기본기이면서 AI 생성 코드의 환각·회귀를 막는 가장 직접적인 장치 |
| P0 | 외부 URL 수집 보안과 HTTP 회복탄력성 | LinkPocket이 사용자 URL과 신뢰할 수 없는 웹 문서를 직접 다루므로 제품의 본질적인 문제 |
| P0 | 인증·인가·세션과 API 남용 방지 | web·extension·magic link라는 서로 다른 client와 사용자 데이터 경계를 안전하게 연결하는 기본기 |
| P0 | 재시도 가능한 비동기 파이프라인 | 크롤링·요약·임베딩은 느리고 실패 가능한 외부 작업이므로 동기 요청에서 분리해야 함 |
| P0 | DB 모델·트랜잭션·실행 계획 | 링크, 작업 상태, 재처리, 아카이브 조회가 모두 RDB의 정확성과 성능에 의존 |
| P0 | 검색·요약 품질 평가와 AI 보안 | 단순 API 호출이 아니라 LinkPocket만의 AI 역량을 보여 주는 핵심 |
| P0 | 관측 가능성·k6·운영 자동화 | 배포 후 1~2개월 운영을 기능 개발과 구분되는 실무 증거로 바꿈 |
| P0 | AI 개발 하네스와 여섯 가지 한계 실험 | Claude/Codex를 썼다는 사실보다 통제·검증·개선한 능력을 보여 줌 |

Kafka·캐시·JVM/GC·tcpdump·SSE/Netty의 보류·조건부 도입 판단은 [decisions/conditional-tech-adoption.md](../decisions/conditional-tech-adoption.md)로 분리했다. 구조를 Modular Monolith로 두는 이유는 [decisions/adr-001-modular-monolith.md](../decisions/adr-001-modular-monolith.md).

핵심 포트폴리오 문장은 "많이 넣었다"가 아니라 다음이어야 한다.

> LinkPocket의 느리고 실패 가능한 수집·요약·검색 파이프라인을 테스트 가능한 상태 머신으로 설계하고, 외부 호출·검색 품질·운영 지표를 측정했다. AI 코딩은 명세, 테스트, 권한, 의존성 가드레일 아래에서 사용했고 한계별 전후 결과를 공개했다.

## 2. 선별 기준

후보는 다음 네 질문을 모두 통과할수록 우선순위를 높였다.

1. LinkPocket의 실제 사용자 흐름에서 실패할 수 있는 지점인가?
2. Java/Spring 신입 백엔드가 설명할 수 있어야 하는 기본 원리인가?
3. 코드가 아니라 테스트·지표·장애 기록으로 증명할 수 있는가?
4. 최근 채용 공고와 국내 서비스 기술 사례에서 반복되는 역량인가?

채용 공고에서 읽히는 공통 신호와 그 근거는 [reference/career-narratives.md](../reference/career-narratives.md) 참고.

---

## 3. 학습 → 적용 매트릭스

각 학습 항목은 `[P0]`/`[P1]`로 구분한다. P0는 처음부터 적용, P1은 제품이 서고 실측 신호가 붙을 때 P0로 승격한다.
아래는 **무엇을 학습하고 무엇을 적용하는가**의 표만 담는다. 구현·운영 정책 상세는 각 섹션 끝의 포인터를 따라간다.

### A. AI 개발 하네스 (0주차 셋업)

| 학습할 것 | LinkPocket에 적용할 것 | 검증·기록할 증거 |
|---|---|---|
| [P0] context engineering | 짧은 `CLAUDE.md`, architecture map, module별 invariant와 명령어만 상시 제공 | 동일 task의 token, turn, 읽은/수정한 파일 수 전후 |
| [P0] progressive disclosure와 Skills | `feature-tdd`, `search-evaluation`, `performance-experiment`, `security-review`처럼 반복 절차만 skill화 | skill 사용 전후 누락 단계·재작업·실패율 |
| [P0] deterministic script의 역할 | lint, test, OpenAPI diff, dependency check, benchmark 결과 수집은 script/CI가 수행 | AI가 매번 해석하던 절차를 자동화한 시간·token 감소 |
| [P0] 에이전트 루프 설계 (loop engineering) | `plan→구현→검증(script/CI)→관찰→수정`을 한 사이클로 고정. 매 반복 같은 결정론적 게이트로 수렴시키고, 자율 반복(`/loop`류)은 acceptance criteria·turn/시간 예산·hook 가드레일 안에서만 실행 | 수렴까지 반복 수, drift(관련 없는 파일 수정 수), 무한루프 차단 여부, self-correction 성공률 |
| [P0] hooks와 최소 권한 | secret 파일 읽기, destructive shell, 운영 DB write, 무승인 deploy를 차단 | hook 차단 로그와 허용/거부 정책 문서 |
| [P1] MCP의 경계 | 개발용 MCP는 GitHub·문서·metric·read-only schema부터 시작 | MCP별 권한표, 반환 데이터 크기, 감사 로그 |
| [P0] 의존성 관리 | 새 라이브러리는 표준 API/기존 의존성 대안, 유지보수·취약점·크기를 검토 | 추가/기각 사유, dependency lock, SBOM, 이미지·빌드 시간 |
| [P0] Human-in-the-loop | 모델·prompt·schema·권한·배포 결정은 사람이 승인 | AI 제안 중 거절·수정한 대표 사례와 판단 근거 |

**루프 엔지니어링은 위 항목들을 하나의 수렴하는 루프로 엮는 일이다.** `deterministic script`가 매 반복의 채점기(게이트)가 되고, `hooks`가 파괴적 행동을 막는 가드레일이 되며, Human-in-the-loop가 종료·승인 지점이 된다. 관리 대상은 루프가 **수렴**하는지(반복마다 게이트 통과에 가까워지는지)와 **발산**하는지(같은 실패를 맴돌거나 관련 없는 파일을 건드리는지)이다. 자율 반복은 종료 조건과 turn/시간 예산 없이는 돌리지 않는다 — 이건 "빨리 짜기"가 아니라 "실패를 스스로 잡고 멈출 줄 아는 루프를 설계하기"다. 루프 전후 지표(수렴까지 반복 수, drift)는 [experiments/ai-limits-experiments.md](../experiments/ai-limits-experiments.md)의 컨텍스트·회귀 실험과 붙여 증거로 남긴다. 반복되는 실패는 **승격 규칙**으로 skill/hook에 흡수한다 → [development-loop.md](../development-loop.md), [operations/mistake-ledger.md](../operations/mistake-ledger.md).

> AI 한계 6종 실험과 실패→복구 서사: [experiments/ai-limits-experiments.md](../experiments/ai-limits-experiments.md)

### B. 명세·계약·테스트 (1주차)

| 학습할 것 | LinkPocket에 적용할 것 | 검증·기록할 증거 |
|---|---|---|
| [P0] Red-Green-Refactor와 테스트 가능한 설계 | URL 정규화, 작업 상태 전이, 멱등 처리, 검색 필터부터 실패 테스트 작성 | red→green→refactor가 보이는 작은 커밋 3~5개 |
| [P0] 테스트 더블과 단위/통합/계약/E2E의 책임 | 도메인 단위 테스트, Testcontainers DB·벡터 DB 통합 테스트, 외부 HTTP stub, 저장→검색 핵심 E2E | 테스트 피라미드, 계층별 잡은 결함, 전체 실행 시간 |
| [P0] API 계약과 타입·스키마 검증 | OpenAPI를 API의 SSOT로 두고 요청/응답·오류 코드·상태 enum을 자동 검증 | 구현과 문서 불일치를 CI에서 실패시킨 사례 |
| [P1] property-based·fuzz testing | URL, redirect, Unicode, HTML, 압축 응답, chunk 경계에 임의 입력 생성 | 사람이 작성한 예제로 찾지 못한 edge case |
| [P1] mutation testing | 상태 머신과 검색 필터 핵심 로직에 mutation test를 적용 | 테스트가 존재하지만 결함을 못 잡은 지점과 보강 전후 mutation score |
| [P0] 명세 기반 개발(SDD)의 역할과 한계 | 기능마다 acceptance criteria·불변식·실패 조건을 먼저 쓰고 AI 계획과 대조 | spec/plan/task/구현 사이 누락 건수, 수정 turn 수 |
| [P0] 결정론적 테스트와 확률적 AI 평가의 차이 | 보안·권한·schema는 항상 PR 차단, 검색·요약 의미 품질은 고정 corpus와 반복 평가 | hard gate와 advisory/nightly gate를 분리한 CI 정책 |

중요한 원칙은 **AI가 구현 코드와 테스트를 동시에 마음대로 정의하게 하지 않는 것**이다. 사람이 먼저 행동 계약과 대표 실패 테스트를 정하고, AI는 구현·추가 케이스·리팩터링을 돕게 한다. property-based/fuzz와 mutation test는 P1로 시작하되 반복 edge case나 escaped defect가 관찰되면 해당 경로에 한해 P0로 승격한다.

### C. DB 모델·트랜잭션·실행 계획 (1주차)

| 학습할 것 | LinkPocket에 적용할 것 | 검증·기록할 증거 |
|---|---|---|
| [P0] 정규화, snapshot/reference, versioning | Link, Content, Chunk, Job, Feedback를 분리하고 parser/model version 보존 | ERD와 모델 선택 ADR |
| [P0] unique constraint와 idempotent write | 사용자+canonical URL, content hash, job idempotency key의 책임 구분 | 동시 중복 저장 100회에서도 의도한 row 수 유지 |
| [P0] transaction 격리와 lock | 여러 worker의 job claim, 사용자 삭제와 vector 삭제 전파 | worker 1/4/16 동시 실행 시 중복 처리·lock wait |
| [P0] B-Tree·복합 인덱스·selectivity | 사용자별 최신 링크, 상태별 작업, 재처리·digest 후보 조회 | 1만/10만/100만 건에서 `EXPLAIN ANALYZE` 전후 |
| [P0] keyset pagination | `(user_id, created_at, id)` 기반 archive pagination | offset과 뒤 페이지 p95 비교 |
| [P0] N+1과 fetch 전략 | 링크 목록에서 tag·summary·status를 가져오는 쿼리 수 제한 | 목록 크기별 SQL 수와 p95 |
| [P0] connection pool과 transaction duration | 외부 HTTP·LLM 호출을 transaction 밖으로, Hikari acquire/usage 관측. 풀 상한은 계산이 아니라 부하로 DB 포화점을 찾아 그 아래로 | transaction 단축 전후, Hikari active/pending, Neon pooler 비교, 부하 단계별 TPS 정체·포화점 |
| [P0] migration과 rollback | model/parser version 컬럼과 상태 enum을 expand/contract 방식으로 변경 | 구·신 버전 호환, migration/rollback 리허설 |
| [P1] 삭제 전파와 파기 상태 머신 | 사용자 탈퇴·링크 삭제 시 DB, vector, object storage, cache의 파기를 작업 체인으로 | 저장소별 삭제 성공·재시도·고아 vector 0건 검증 |

> 삭제 전파는 외부 사용자를 받기 전 P0로 승격. Neon 운용 제약·삭제 파이프라인 상세: [architecture/database-and-infra.md](../architecture/database-and-infra.md), [operations/data-privacy-and-rights.md](../operations/data-privacy-and-rights.md)
> 커넥션 풀 상한을 계산(Little's Law)이 아니라 부하로 DB 포화점을 찾아 정하는 실험: [experiments/exp-01-connection-pool-sizing](../experiments/exp-01-connection-pool-sizing/README.md)

### D. 외부 URL 수집: 네트워크·보안 (2주차)

| 학습할 것 | LinkPocket에 적용할 것 | 검증·기록할 증거 |
|---|---|---|
| [P0] DNS→TCP→TLS→HTTP 요청 흐름 | fetch 단계별 지연과 실패 유형을 구분해 기록 | `dns/connect/tls/ttfb/read` 구간별 지표와 오류 taxonomy |
| [P0] SSRF, redirect 우회, DNS rebinding | 허용 scheme 제한, private/link-local/metadata IP 차단, redirect마다 host·resolved IP 재검증 | localhost, RFC1918, IPv6 local, metadata, redirect 우회 보안 테스트 |
| [P0] connect/response/read timeout 차이 | 하나의 `timeout=3s`가 아니라 실패 구간별 예산 설정 | 느린 테스트 서버에서 어떤 timeout이 발생했는지 재현 |
| [P0] retry 가능/불가능 오류 | timeout·429·일부 5xx만 제한 재시도, 4xx·형식 오류·과대 문서는 즉시 종료 | retry 횟수, 최종 성공률, 중복 요청, 불필요한 호출 비용 |
| [P0] exponential backoff·jitter·retry budget | 동일 도메인 장애 시 재시도 폭주 방지 | 장애 복구 시점의 요청 spike 전후 비교 |
| [P0] HTTP connection pool과 keep-alive | global/per-host max, pending acquire timeout, idle/lifetime 설정 | active/idle/pending, 새 연결률, 처리량, 오류율 |
| [P0] bounded concurrency와 backpressure | 전체·사용자·도메인별 동시 수집 제한, queue 상한과 거부/지연 정책 | concurrency 1/4/16/64에서 처리량·429·pool wait·메모리 비교 |
| [P1] streaming과 입력 크기 제한 | HTML/PDF를 무제한으로 읽지 않고 압축 해제 크기·본문 크기 제한 | 큰 문서 처리 시 heap peak와 차단 시점 |
| [P0] 간접 prompt injection | 수집 문서는 명령이 아닌 비신뢰 데이터로 취급, tool 실행·secret 접근과 분리 | 숨은 지시문, prompt 탈취, 외부 전송 유도 문서 공격 테스트 |
| [P1] robots.txt·User-Agent·저작권 | RFC 9309, 식별 가능한 crawler identity, domain politeness. 공개 서비스 전 P0 승격 | robots fixture, domain별 요청 간격·429율, 수집 중단 runbook |

`connection pool`, `max connections per route`, `thread 수`는 설정값 암기가 아니라 **도착률, 평균 처리시간, 허용 동시성, downstream 한도**를 근거로 계산하고 k6·장애 주입으로 확인한다.

> 책임 있는 크롤러 운영 정책: [operations/data-privacy-and-rights.md](../operations/data-privacy-and-rights.md) 3절

### E. 비동기 파이프라인 (3주차)

| 학습할 것 | LinkPocket에 적용할 것 | 검증·기록할 증거 |
|---|---|---|
| [P0] 동기와 비동기의 선택 기준 | 저장 API는 링크·작업을 기록 후 빠르게 응답, fetch→parse→embed→index는 background | 동기 baseline과 비동기 구조의 p95, 실패 격리, 상태 표현 비교 |
| [P0] 명시적 상태 머신 | `PENDING→FETCHED→CHUNKED→INDEXED`, 실패·중단·재처리 상태와 허용 전이 | 허용되지 않은 전이와 중복 완료를 막는 테스트 |
| [P0] at-least-once와 멱등성 | `linkId/jobId/inputHash/parserVersion/modelVersion`으로 중복 실행 결과 고정 | 같은 job 2회, 처리 직후 프로세스 종료, 늦은 event 재현 |
| [P0] transaction 경계와 outbox | 링크 저장과 최초 작업 생성은 같은 transaction, HTTP/LLM 호출은 DB transaction 밖 | commit 직후 process kill, publisher/worker 장애에서 유실·중복 |
| [P0] 재시도·DLQ보다 오류 분류 | retryable/non-retryable 구분, 최대 횟수·다음 실행 시각, 수동 재처리 | 오류 이유별 건수, 재처리 성공률, oldest job age |
| [P0] queue·worker capacity | worker 수, HTTP/DB connection, LLM rate limit을 함께 제한 | worker 증가가 언제 throughput 대신 pool wait·429만 늘리는지 |
| [P0] 자체 API rate limiting | 사용자·IP별 저장/검색 한도를 endpoint 위험도에 맞게 적용 | token bucket 원자성, 허용 burst, 정상 사용자 차단률, 429 계약 |
| [P0] 운영 가시성 | 링크별 현재 단계·오류 이유·재시도 횟수·마지막 성공 결과 조회 | 사용자 상태 화면과 운영용 pipeline 대시보드 |
| [P1] polling과 부하 제어 | 서버가 다음 조회 시점·terminal 상태를 계약하고 client는 backoff+jitter | polling 요청 수, 완료 인지 지연, 동시 처리 링크 수별 RPS |

비동기는 "빠르게 보이게 하는 기술"이 아니라 **느리고 실패 가능한 작업을 분리하고, 상태·재시도·복구 책임을 명시하는 설계**로 설명한다.

> 상태 노출 정책·scheduler·메일 발송: [architecture/async-pipeline.md](../architecture/async-pipeline.md)
> rate limiting 상세·Job→Kafka 진화: [operations/ai-cost-and-rate-limits.md](../operations/ai-cost-and-rate-limits.md), [decisions/conditional-tech-adoption.md](../decisions/conditional-tech-adoption.md)

### F. 검색·요약 품질과 AI 안전성 (4~5주차)

| 학습할 것 | LinkPocket에 적용할 것 | 검증·기록할 증거 |
|---|---|---|
| [P0] semantic retrieval의 제품 경계 | 저장된 Link를 찾아 결과와 기존 AI 요약을 보여 주고, 답변 생성은 제외 | retrieval 결과와 생성형 답변의 범위 구분 |
| [P0] HTML 정규화와 chunking | 제목·heading·code block·문단 구조를 보존한 chunk 전략 | chunk size/overlap별 Recall@K·index 크기·비용 |
| [P0] sparse/BM25, dense, hybrid retrieval | 에러 코드·고유명사 질의와 의미 질의를 분리해 baseline 비교 | query 유형별 Recall@5, MRR@10, zero-result rate |
| [P0] metadata filter와 권한 경계 | 모든 검색에서 서버가 userId filter를 강제, 모델 입력으로 권한 결정 안 함 | tenant leakage=0인 통합·공격 테스트 |
| [P1] top-K·threshold·reranking | 후보 수와 reranking 적용 범위를 품질·지연·비용으로 결정 | quality/latency/cost trade-off 표 |
| [P0] golden dataset과 offline evaluation | 실제 시나리오 50~100개 query-relevant link pair와 hard negative 구축 | 모델·prompt·index 변경 전 회귀 평가 |
| [P0] 요약 충실도와 사용자 보정 | 파서·요약 결과와 사용자 수정값을 분리하고, 수정 뒤 해당 Link만 재색인 | 형식·길이·빈 결과, 표본 충실도, 재색인 범위 |
| [P0] prompt/model/parser versioning | 생성 결과와 입력 hash·모델·prompt·parser version 연결 | 변경 전후 품질과 부분/전체 재색인 범위 |
| [P1] AI dependency fallback | AI 장애 시 링크 저장·원문 조회·keyword search는 유지 | 모델/vector DB 장애 주입 시 핵심 기능 생존 여부 |
| [P0] provider rate limit | 요청/토큰 제한, `Retry-After`, 429·5xx·4xx 구분해 model별 concurrency 제한 | 429율, retry amplification, queue wait, 성공까지 호출 수 |
| [P0] token·비용 회계 | 기능·사용자·모델별 token과 원화 환산 비용을 generation ID에 기록 | cost/link, cost/query, 월 누적·예측 비용, 예산 초과 횟수 |
| [P1] 비용 기반 cache | `contentHash+modelVersion+promptVersion` 결과 재사용, conditional fetch | 절감 호출 수·비용, stale 결과, cache invalidation 정확도 |

`임베딩을 만들었다`보다 `어떤 질의에서 dense 검색이 실패했고 hybrid가 왜 나았는지, 품질 향상과 비용을 어떻게 함께 판단했는지`가 훨씬 강한 AI 프로젝트 증거다.

> 검색·요약 CI 게이트: [operations/observability-slo-kpi.md](../operations/observability-slo-kpi.md) 4절
> LLM 비용·rate limit·cache 정책: [operations/ai-cost-and-rate-limits.md](../operations/ai-cost-and-rate-limits.md)

### G. 관측 가능성·성능·운영 (6~8주차)

| 학습할 것 | LinkPocket에 적용할 것 | 검증·기록할 증거 |
|---|---|---|
| [P0] OpenTelemetry와 로그·메트릭·trace의 역할 | requestId/linkId/jobId/traceId로 저장→색인 전체를 연결하고 OTLP로 전송 | 한 실패를 API 로그, job row, metric, trace에서 추적 |
| [P0] RED/USE와 비즈니스 지표 | API rate/error/duration, pool/CPU/memory와 save→index 성공률·oldest job age | 사용자 영향과 자원 지표를 연결한 대시보드 |
| [P0] SLI/SLO와 alert | save API, index-ready, search, 연관 추천에 소수의 SLO 설정 | alert가 임계값이 아니라 사용자 영향과 연결된 근거 |
| [P0] 제품 KPI와 기술 SLO의 연결 | 다시 열람·검색 성공·retention·digest CTR을 기술 실패율·지연과 cohort로 관찰 | 기능이 빠른데 안 쓰이는 경우와 느려서 이탈한 경우 구분 |
| [P0] k6 workload modeling | 저장 burst, archive/search, 연관 추천, 재색인을 서로 다른 시나리오로 실행 | arrival rate, 데이터 크기, p50/p95/p99, error rate 원본 |
| [P1] profiling과 한 변수 실험 | 느린 경로를 SQL plan, JFR, HTTP/pool metric 중 증거가 가리키는 도구로 분석 | baseline→가설→한 변수 변경→재측정 보고서 |
| [P1] dependency fault injection | fetch origin, DB, vector DB, embedding/LLM에 지연·429·5xx·중단 주입 | retry amplification, queue 회복 시간, graceful degradation |
| [P0] 배포·rollback·migration | CI test→image scan→deploy→smoke test, feature flag와 rollback | 실패 배포 또는 리허설의 복구 시간·절차 |
| [P0] 운영 회고 | 사용자 피드백, 실패 URL 유형, 비용, false alert, 품질 회귀를 주간 집계 | 48시간·2주·2개월 운영 회고와 action item |

실제 사용자가 적더라도 운영 경험은 만들 수 있다. 사용자를 가장하지 말고, **실제 사용자 지표와 합성 부하·장애 실험을 명확히 분리**해 기록한다.

> OTel 스택 ADR·제품 KPI 지표 상세: [operations/observability-slo-kpi.md](../operations/observability-slo-kpi.md)

### H. 인증·인가와 세션 (1~2주차)

| 학습할 것 | LinkPocket에 적용할 것 | 검증·기록할 증거 |
|---|---|---|
| [P0] 인증과 인가의 경계 | 로그인 성공과 `이 사용자가 이 link/vector를 읽을 수 있는가`를 분리, 모든 query에서 tenant를 서버가 결정 | IDOR·tenant leakage 테스트 |
| [P0] JWT 서명·claim·수명 | issuer/audience/exp/nbf 검증, 짧은 access token, 최소 claim, key rotation | 만료·잘못된 aud/iss·과거 key·변조 token 거부 |
| [P0] Refresh Token rotation | refresh마다 새 token 발급, 이전 token hash·family·device session 폐기 | 이전 token 재사용 시 token family 전체 폐기 |
| [P0] access token revocation | 짧은 TTL 기본, 즉시 로그아웃·탈취 대응 세션만 jti/session version으로 차단 | logout 후 재사용, blacklist 장애, TTL 만료 시나리오 |
| [P0] OAuth Authorization Code+PKCE | extension은 PKCE S256, one-time state/nonce, exact redirect URI allowlist | code injection, state mismatch, open redirect |
| [P0] web과 extension 세션 분리 | web은 HttpOnly·Secure·SameSite cookie, extension은 one-time code 교환 후 별도 device session | 한 client 로그아웃/탈취가 다른 client에 미치는 범위 |
| [P0] CSRF와 XSS의 차이 | cookie 인증엔 CSRF 방어, Authorization header token엔 XSS·token 탈취 방어 | cross-site form/fetch, Origin 검증, CSP |
| [P0] magic link | purpose/audience/redirect allowlist 있는 random nonce를 hash 저장, 짧은 TTL·1회 사용 | 만료·재사용·다른 계정/목적·redirect 조작 테스트 |
| [P1] 소셜 provider별 차이 | Google·GitHub의 scope·identifier·email 신뢰도·revoke 차이를 adapter 경계와 ADR로 | provider fixture와 동일 내부 identity/session 계약 |
| [P1] 권한 변경·감사 | 계정 정지, 동의 철회, token family 폐기, 관리자 조치를 audit event로 | 세션 폐기 추적, token 원문 미기록 |

> 두 클라이언트 세션 경계·불변식 상세: [architecture/auth-and-session.md](../architecture/auth-and-session.md)

---

## 4. 하루 6시간 기준 8주 실행 순서

| 주 | 제품 목표 | 학습·적용 | 대표 증거 |
|---:|---|---|---|
| 0 | AI 개발 하네스 셋업 | context map, Skills, deterministic scripts, hooks·권한, dependency guard, **에이전트 루프 게이트 설계** | baseline task와 하네스 적용 후 token·turn·오수정 비교, 루프 수렴까지 반복 수 |
| 1 | 인증+저장·목록 vertical slice | SDD/TDD, OAuth Code+PKCE, web/extension session, OpenAPI | 로그인 공격 테스트, 실패 테스트 커밋, API contract CI |
| 2 | 안전한 URL fetcher | HTTP 흐름, SSRF, timeout/retry/pool | 보안 공격표, 외부 장애 integration test |
| 3 | 비동기 처리 pipeline | 상태 머신, transaction, idempotency, backpressure | 중복·crash·재시도 시나리오 |
| 4 | semantic/hybrid search | chunking, dense/BM25/hybrid, tenant filter | 50개 이상 golden set과 Recall/MRR |
| 5 | P1 주간 다이제스트 | 미열람 후보·클러스터링·snooze·메일 안전성 | 품질·보안·비용 eval report |
| 6 | private alpha 배포 | 핵심 저장·검색, 최소 OTel, smoke test, backup 확인 | alpha 사용자 흐름과 첫 48시간 실패 목록 |
| 7 | 운영 기반 보강 | OTel dashboard, 비용 알람, k6, rollback·삭제 전파 리허설 | SLO 초안, runbook, 1주 운영 회고 |
| 8 | 공개 출시와 실험 1개 | 디스콰이엇 공개, Kafka 진화·AI 한계·MCP 중 하나만 심화 | ADR, 재현 script, 원본 수치, 출시 회고 |

8주차 실험은 다음 점수로 고르고 선택 근거를 ADR 첫 문단에 남긴다.

```text
지원하려는 공고와의 직접 연관성 40
+ 6~7주차 실제 지표·장애가 보여 준 필요성 40
+ 1주 안에 재현·비교·문서화할 완결 가능성 20
```

- Kafka는 queue/outbox 신호가 있을 때, AI 한계 실험은 회귀·컨텍스트 문제가 실제 발생했을 때, MCP는 검색 품질과 권한 경계가 안정됐을 때 우선한다.
- P0 보안·데이터 유실·사용자 차단 문제가 열려 있거나 alpha 핵심 SLO가 깨지면 8주차 심화 실험은 취소하고, 해당 사건을 재현 테스트와 postmortem으로 전환한다.
- 사용자 대응이 주 6시간 이상을 잠식하면 실험 범위를 `한 workload·한 변수·한 결과표`로 축소한다.

### 하루 배분 예시

| 시간 | 활동 |
|---:|---|
| 3시간 | 그 주의 end-to-end 기능을 TDD로 구현 |
| 1.5시간 | 기능에 직접 연결되는 CS 학습과 최소 재현 |
| 1시간 | 통합/부하/보안/품질 실험과 원본 데이터 저장 |
| 0.5시간 | 학습 아티클·ADR·실험 일지 정리 |

목요일이나 금요일 하루를 실험일로 고정해 기능 개발과 측정 작업이 서로 밀려나지 않게 한다. 출시 뒤에는 신규 기능보다 실패 URL, 품질, 비용, 사용자 피드백 대응을 우선한다.

---

## 관련 문서

- 출시 전 최종 체크리스트(Definition of Done): [../release-checklist.md](../release-checklist.md)
- 취업용 산출물·블로그·회사 유형별 서사: [reference/career-narratives.md](../reference/career-narratives.md)
- 이 로드맵의 각 축을 깊게 정리한 학습 아티클: [articles/](articles/) (쓰기 전 [learning/README.md](README.md)와 이 파일의 해당 축을 읽는다)
