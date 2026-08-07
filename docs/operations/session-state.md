# 세션 상태

> 다음 세션(사람이든 Claude·Codex든)이 `git log`·`git status`·PR 목록을 매번 재구성하지 않도록, 의미 있는 작업 단위가 끝날 때마다 이 파일을 갱신한다. **최신 상태만 남긴다** — 과거 이력은 git log가 이미 갖고 있으니 여기 쌓지 않는다.

## 완료
- **plan-01(auth) 완전히 완료.** PKCE·rotation/reuse detection·DB 세션 저장소(Spring Session JDBC)·**익스텐션 refresh token 14일 고정 만료**(rotation은 유지, 그 위에 로그인 유지 기간 상한만 추가)까지 전부 머지됨.
- **plan-02(link-save) 완전히 완료.** 멱등 저장(unique constraint+ON CONFLICT) + 확장(저장 취소/조회 토글, `alreadyExisted` 플래그, 추적 파라미터 제거) 전부 머지됨.
- **plan-03(safe-fetch-extract) 완전히 완료.** SSRF 방어(DNS+IP+redirect 재검증)·timeout 3구간·재시도·20MB 상한·PDF 거부·중복 fetch 방지·본문 추출(readability4j) 전부 머지됨.
- **개발 프로세스 자체를 개정함(가장 중요한 변화)**: Claude가 위험 로직 결정 시 이제 **선택지를 주기 전에 사용자 가치·품질 기준을 먼저 질문**한다(plan/README.md). ADR 템플릿은 6단계 구조(문제 정의→가설→대안→선택→관찰 지표→결과, decisions/README.md)로 개정됨. 이미 구현된 plan-01/02/03도 이 프레임워크로 회고해 실제 구현과 결정이 일치하는지 검증했고, 그 과정에서 실제 버그 2건(세션이 DB가 아닌 인메모리였던 것, 커넥션 풀 미재사용)과 보안 완화 시도 1건(Codex가 테스트 우회하려 `/api/logout` 권한을 풀었던 것)을 잡았다.
- 학습 아티클 정책 확정: pre-hoc만 사용, 트리거는 "위험 로직 자체"(①)와 "백엔드 핵심 주제 체크리스트"(②, learning/articles/README.md에 5개 영역 명시)로 두 갈래. 기존 아티클: `idempotency-and-concurrency-control.md`(plan-01/02/03 전체 적용), `html-extraction-...`, `chunking-strategy-...`, `dense-bm25-hybrid-retrieval`(plan-04/06용, 아직 미적용).
- mistake-ledger: `contract-test-authoring`(test-only 값 하드코딩 금지) 3회 승격 완료 → development-loop.md 규칙 7. `adr-implementation-drift`(ADR과 실제 구현 불일치) 신설 카테고리, 1회.

## 결정과 근거
- ADR-006 결정 3 최종본: rotation/reuse-detection(탈취 감지)은 그대로 두고 `device_session.created_at` 기준 **14일 고정 만료**(슬라이딩 아님)만 추가. "rotation을 대체"가 아니라 "그 위에 얹는다"는 점이 중요 — 회고 대화 중 범위를 잘못 이해할 뻔했다가 바로잡음.
- 리뷰 시 발견한 문제는 즉시 Codex에게 재작업을 요청하는 걸 표준 절차로 삼음(승인 없이 진행하지 않되, 발견 즉시 사람에게 보고 후 재작업 프롬프트 전달).

## plan-04(async-ai-pipeline) 진행 — 사람 승인 완료, 계약 테스트(빨강) 작성 완료
- [ADR-012](../decisions/adr-012-async-pipeline-job-claim-and-idempotency.md) — job claim(`SKIP LOCKED`, 브로커 없이 DB만), 트랜잭션 경계(LLM 호출 tx 밖 + `lease_expires_at` 복구), 멱등성(호출 전 조회+unique constraint).
- [ADR-013](../decisions/adr-013-chunking-strategy.md) — chunking: 구조 보존 우선 + 고정 크기 fallback + overlap. OCR(이미지 텍스트)은 MVP2로 제외.
- [ADR-014](../decisions/adr-014-ai-processing-failure-exposure-and-retry.md) — AI 처리 영구 실패 시 목록엔 유지·요약란에 실패 메시지, 재시도 불가 사유(4xx류)는 즉시 확정·재시도 가능 사유(5xx·일시장애)만 최대 2회.
- [plan/04-async-ai-pipeline.md](../plan/04-async-ai-pipeline.md) 사람 승인 완료.
- **계약 테스트 작성 완료(`src/test/java/com/linkpocket/contract/link/`)**: `LinkProcessingClaimConcurrencyContractTest`(SKIP LOCKED 배타성), `LinkProcessingLeaseRecoveryContractTest`(lease 만료 재claim), `LinkProcessingIdempotencyContractTest`(재claim 후 중복 LLM 호출 안 함), `LinkProcessingRetryPolicyContractTest`(재시도 가능/불가 구분·최대 2회·콘텐츠 보존), `LinkStatusApiContractTest`(내부→외부 상태 매핑·tenant 격리 IDOR 404 통일). 공통 기반은 `AbstractLinkProcessingContractTest`.
- **설계 결정(테스트 작성 중)**: 실제 OpenAI 연동 대신 `SummaryGenerator`/`EmbeddingGenerator` 순수 인터페이스(seam)를 `src/main/java/com/linkpocket/link/`에 새로 선언하고, 계약 테스트는 `@Primary` Fake 빈으로 대체해 호출 횟수·성공/실패를 결정적으로 제어한다 — LLM 실제 연동은 이 계약 범위 밖(plan-03이 SSRF는 WireMock으로 실제 검증한 것과 달리, 이번 위험 로직은 "LLM을 어떻게 부르는가"가 아니라 "job claim·재시도·멱등성"이라 테스트 대상이 다르기 때문). `pollAndProcessOnce()`를 가진 `LinkProcessingWorker` 인터페이스도 같은 이유로 신설(스케줄 타이밍에 의존하지 않는 결정적 테스트 훅).
- `./gradlew compileTestJava` 통과 확인, `LinkProcessingClaimConcurrencyContractTest` 1건 실행해 `NoSuchBeanDefinitionException`(LinkProcessingWorker 미구현)으로 정상 빨강 확인 — 나머지 4개 파일도 같은 이유로 빨강일 것으로 예상(전체 실행은 안 함, Docker 기반 테스트라 느림).

## 미완료
- 계약 테스트 5개 파일 전체 실행으로 전부 의도대로 빨강인지 확인(1개만 확인함).
- Codex에게 구현 위임: `LinkProcessingWorker`/`SummaryGenerator`/`EmbeddingGenerator` 실제 구현 + Flyway 마이그레이션(`link.processing_lease_expires_at`, `link.processing_attempt_count`, 요약/청크/임베딩 결과 테이블) + `GET /api/links/{linkId}/status` 엔드포인트.

## 다음 시작점
- Codex에게 plan-04 구현 프롬프트 전달(계약 테스트를 고정 타깃으로) → `verify.sh` green 확인 → Claude 리뷰(7축) → 머지 승인.

## 금지
- `src/test/**/contract/**`(계약 테스트) 수정 금지 — Codex뿐 아니라 자동화 전반.
- 학습 아티클에 최종 결정 문구를 쓰지 않는다 — 결정은 ADR에만.
- **위험 로직·기술 선택 시 Claude가 선택지부터 제시하지 않는다** — 사용자 가치·품질 기준을 먼저 묻는다(2026-07-26 절차 개정).

---
갱신: 2026-07-27 · 브랜치: `main`(plan-01/02/03 전체 머지 완료 상태)
