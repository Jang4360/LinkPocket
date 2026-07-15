# 채용 신호·산출물·회사 유형별 서사

> 출처: `learning/cs-learning.md`에서 분리 (1절 채용 신호, 6절 산출물·블로그·서사)

## 1. 최근 공고에서 읽히는 공통 신호

공고 몇 개를 전체 시장 통계처럼 해석하지 않고, 2026년 신입·경력무관 공고와 IT 서비스/SI 성격의 공고에서 반복되는 항목만 추렸다.

| 공고 신호 | LinkPocket에서 만들 증거 |
|---|---|
| Java/Spring, REST API, RDB·SQL | 일관된 API 계약, 데이터 모델, 트랜잭션과 실행 계획 |
| 테스트 코드와 코드 품질 | TDD 커밋, 계층별 자동화 테스트, CI 차단 기록 |
| AI 에이전트를 지시·설계·관리하는 경험 | 명세, context map, skill, hook, 실험 결과로 AI 작업을 통제한 기록 |
| 외부 시스템 연동과 운영·장애 대응 | timeout/retry/pool, 멱등성, 상태·오류 대시보드, 회고 |
| Redis/Kafka·이벤트 처리 | 필요 조건과 대안 비교 후 선택 또는 기각한 ADR |
| SQL 분석·튜닝, 모니터링, CI/CD | 실행 계획 전후, SLO/대시보드, 자동 배포·rollback 리허설 |

근거 예시(원문은 [sources.md](sources.md)):
- 에코마케팅 경력무관 공고는 Java/Spring·RDB·REST와 함께 AI 에이전트를 지시·설계·관리한 경험, Kafka/Kinesis, API 문서와 테스트를 함께 언급한다.
- 2026년 현대오토에버 신입 모집요강의 백엔드 직무는 Java/Spring Boot, RDBMS·SQL, 테스트 코드를 기본으로 두고 일부 직무에서 AI Coding, Redis, Kafka, SQL 튜닝을 우대한다.
- NC IDS 공고는 경력직이므로 신입의 필수 기준으로 쓰지 않고, 실행 계획·Kafka·TDD·CI/CD·Prometheus/Grafana까지 깊어지는 장기 성장 방향을 확인하는 용도로만 사용한다.

## 2. 취업용으로 남길 최소 산출물

### 저장소
- architecture diagram 1장과 핵심 흐름 설명
- 핵심 ADR 5~7개: modular monolith, 비동기 진화, retrieval, cache, MCP/권한, OpenTelemetry backend
- OpenAPI와 상태 머신 문서
- Testcontainers 기반 통합 테스트와 CI
- k6·검색/요약 eval·AI 한계 실험의 실행 script와 raw result
- 운영 dashboard screenshot, runbook, postmortem

### 대표 블로그 글 후보
1. `AI가 만든 코드를 믿기 위해 TDD보다 먼저 정한 것: LinkPocket의 계약과 불변식`
2. `사용자 URL을 받는 순간 검색 서비스가 보안 서비스가 된 이유`
3. `동기 크롤링을 비동기 작업 체인으로 바꾸며 상태·재시도·멱등성을 설계한 과정`
4. `Dense 검색이 늘 정답은 아니었다: LinkPocket의 BM25·Hybrid·Rerank 평가`
5. `Kafka를 넣고 싶었지만 DB Queue로 출시한 이유와 replay 비교 실험`
6. `Claude/Codex 하네스 적용 전후: token보다 회귀·권한·의존성을 어떻게 줄였나`
7. `디스콰이엇 출시 후 2개월: 합성 부하와 실제 사용자를 구분해 운영한 기록`

> 초안은 [learning/articles/](../learning/articles/)에서 작성, 발행 링크는 여기 `blog/`에 기록.

### 대표 경험 6가지 (지원 회사와 가까운 3~5개를 수치·실패·트레이드오프까지)
1. AI 생성 코드를 명세·TDD·CI로 통제한 경험
2. 외부 URL/AI 의존성의 장애와 보안을 설계한 경험
3. 비동기 상태 머신과 멱등 재처리를 검증한 경험
4. 검색·요약 품질을 golden set과 검색 지표로 개선한 경험
5. 출시 후 지표·장애·피드백을 보고 한 가지 결정을 바꾼 경험
6. AI가 실제로 만든 결함과 가드레일을 보강해 재발을 막은 경험

## 3. 지원 회사 유형별 서사 매핑

| 지원 방향 | 앞에 둘 LinkPocket 서사 | 보조 증거 |
|---|---|---|
| 배달·커머스·핀테크 서비스 | 상태 머신, 멱등 재처리, Outbox 진화, 외부 API 장애 격리 | polling UX, rate limit, OTel 장애 타임라인 |
| 토스·카카오페이형 AI/생산성 조직 | 검색·요약 CI, 비용·rate limit 회계, AI 실패→하네스 개선 | SDD/TDD, MCP 최소 권한, dependency guard |
| 검색·콘텐츠·플랫폼 | hybrid retrieval 평가, 책임 있는 crawler, 제품 KPI | headless 도입 판정, tenant filter, 삭제 전파 |
| SI·대기업 IT 서비스 | Java/Spring, 인증·인가, SQL 실행 계획, transaction·pool, 테스트 자동화 | 관리형 DB 제약, CI/CD, runbook·문서화 |
| 인프라·플랫폼 성격 | OTLP/Collector, queue lag·capacity, Kafka 전환 신호 | k6, JFR/GC·tcpdump 조건부 진단 |

같은 프로젝트를 회사마다 다른 기술 목록으로 포장하지 않는다. 공고의 문제와 가장 가까운 2~3개 사건을 앞에 두고 나머지는 깊이를 증명하는 보조 자료로 연결한다.
