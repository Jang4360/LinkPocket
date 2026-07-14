# 출시 전 최종 체크리스트 (Definition of Done)

> 출처: `learning/cs-learning.md`에서 분리 (8절)
> 프로젝트 전반을 가로지르는 완료 기준. 각 항목의 상세 정책은 괄호 안 문서 참고.

## 반드시 완료

- [ ] Claude/Codex의 여섯 한계 중 최소 4개를 동일 조건 전후 실험으로 남긴다. ([experiments/ai-limits-experiments.md](experiments/ai-limits-experiments.md))
- [ ] 계획된 실험과 별개로 실제 AI 실패→발견→가드레일 보강 서사 1건이 있다.
- [ ] OpenAPI·acceptance criteria·실패 테스트가 구현보다 먼저 존재한다.
- [ ] 대표 archive/job 쿼리의 실행 계획과 데이터 크기별 결과가 있다. ([architecture/database-and-infra.md](architecture/database-and-infra.md))
- [ ] 외부 HTTP/LLM 호출이 긴 DB transaction 안에 있지 않다.
- [ ] 사용자 탈퇴·링크 삭제가 DB/vector/cache에 멱등하게 전파되고 orphan 검증이 있다. ([operations/data-privacy-and-rights.md](operations/data-privacy-and-rights.md))
- [ ] 열람/export·정정·처리정지·동의 철회 요청 경로와 실제 구현에 맞는 개인정보처리방침이 있다.
- [ ] Neon pooled/direct endpoint, transaction pooling 제약, auto-suspend cold start를 검증했다.
- [ ] URL fetcher에 SSRF·redirect·timeout·크기 제한 테스트가 있다.
- [ ] robots.txt, 식별 가능한 User-Agent, domain politeness와 수집 중단 요청 runbook이 있다.
- [ ] OAuth state/PKCE/redirect 검증, Refresh Token rotation·reuse detection, magic link 1회 사용 테스트가 있다. ([architecture/auth-and-session.md](architecture/auth-and-session.md))
- [ ] web과 extension session을 분리하고 개별·전체 폐기 경계가 문서화돼 있다.
- [ ] pipeline에 상태, 멱등 키, 재시도 가능 여부, 오류 이력이 있다. ([architecture/async-pipeline.md](architecture/async-pipeline.md))
- [ ] 로그인·저장·검색·RAG 자체 API에 user/IP 위험도별 rate limit과 429 계약이 있다. ([operations/rag-cost-and-rate-limits.md](operations/rag-cost-and-rate-limits.md))
- [ ] RAG golden set과 retrieval/generation 품질 지표가 있다.
- [ ] retrieval hard gate와 generation nightly 평가를 분리한 CI 정책이 있다. ([operations/observability-slo-kpi.md](operations/observability-slo-kpi.md))
- [ ] LLM provider별 429·5xx 정책, token/cost ledger, 70/90/100% 예산 동작이 있다.
- [ ] 저장→색인 전체를 linkId/jobId로 추적할 수 있다.
- [ ] Micrometer/OTLP→OpenTelemetry Collector→관측 backend 경로와 ADR이 있다.
- [ ] 제품 KPI의 이벤트·분자·분모·내부 트래픽 제외 기준이 analytics contract에 있다.
- [ ] k6와 dependency fault 시나리오가 CI 또는 반복 script로 실행된다.
- [ ] 디스콰이엇 출시 48시간·2주·2개월 회고를 남긴다. ([operations/](operations/))

## 조건이 맞을 때 선택 ([decisions/conditional-tech-adoption.md](decisions/conditional-tech-adoption.md))

- [ ] DB queue와 Kafka replay/lag 구조 비교
- [ ] 읽기 전용 LinkPocket MCP prototype
- [ ] 절감 호출 비용을 계산한 뒤 content hash 기반 AI 결과 cache 적용
- [ ] 반복 조회와 병목을 측정한 뒤 성능 cache 적용
- [ ] 동적 렌더링 실패 비율을 측정한 뒤 sandboxed Playwright worker 적용
- [ ] 다중 인스턴스 또는 예약 복잡도에 따라 ShedLock/durable scheduler 적용
- [ ] 대량 재색인에서 JFR/GC 분석
- [ ] 배포/DB cold start 원인을 분리한 뒤 JVM warm-up 적용
- [ ] 네트워크 장애 재현 후 tcpdump 분석

## 하지 않을 것

- [ ] Kafka·Redis·Netty를 사용 기술 목록을 채우기 위해 도입
- [ ] GC 옵션 여러 개를 원인 분석 없이 변경
- [ ] SSE를 쓰기 위해 처리 상태를 실시간 요구사항으로 포장
- [ ] AI에게 테스트의 정답과 구현을 동시에 맡기고 통과만 확인
- [ ] 실제 사용자 지표와 k6 합성 트래픽을 섞어 성과를 과장
- [ ] 모든 기술을 얕게 한 뒤 각각을 대표 경험처럼 이력서에 나열
