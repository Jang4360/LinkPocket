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

## plan-04(async-ai-pipeline) — 완전히 완료·머지 (PR #24)
- [ADR-012](../decisions/adr-012-async-pipeline-job-claim-and-idempotency.md)/[013](../decisions/adr-013-chunking-strategy.md)/[014](../decisions/adr-014-ai-processing-failure-exposure-and-retry.md) 전부 확정·구현 반영.
- `LinkProcessingWorker`(SKIP LOCKED claim + lease 복구) + `OpenAiSummaryGenerator`/`OpenAiEmbeddingGenerator` + `ContentChunker`(구조 보존+fallback+overlap) + `GET /api/links/{linkId}/status` 구현·머지 완료. V7 마이그레이션(`link_summary`/`link_chunk`/`link_embedding`, pgvector 없으면 `real[]` fallback).
- 리뷰 중 버그 2건 발견·수정: 계약 테스트 `@TestConfiguration` 서브클래스 미인식(명시적 `@Import`로 교정), `LinkService.status()`의 pgjdbc `Instant` 미지원 변환(500 유발, `OffsetDateTime` 경유로 교정). `GlobalExceptionHandler`가 예외를 무로그로 삼키는 기존 문제(plan-01부터)는 범위 밖이라 미수정, mistake-ledger에만 기록.

## 인프라·문서 개정 — 완료·머지 (PR #25)
- `기술스택.md` 2-15: OCI ARM 6GB 단일 인스턴스 → **API/Worker 8GB 2인스턴스 분리**로 개정(job claim 설계가 인스턴스 배치 무관이라 코드 변경 없음).
- `docs/evidence/`(신설): `README.md`(Implemented/Observed/Proposed 구분, 이력서엔 Observed만), `claim-ledger.md`, `experience-card-template.md`.
- 로드맵에 두 항목 등록: **알파 종료 직후 최우선** AI 비용 계측·예산 정책 구현(plan 번호 미배정, 09-digest보다 먼저), **`02c-offline-save-queue`**(IndexedDB 오프라인 저장 대기열, 알파 이전 착수 안 함 — 실측 신호 대기).
- `experiments/README.md`·`operations/README.md`에 exp-02~05·alpha-analytics-contract·alpha-feedback-loop·release-and-rollback-evidence를 "착수 시 작성" 백로그로 등록(파일은 아직 없음).
- `conditional-tech-adoption.md`에 Caffeine domain-concurrency 항목 추가.

## plan-05(categories) — 완전히 완료·머지 (PR #27 계약, #29 구현, #28 관련 문서)
- [ADR-015](../decisions/adr-015-category-deletion-and-correction-overwrite.md) — 카테고리 삭제=연결 해제만(Link 보존)+ON DELETE CASCADE+"카테고리 없음" 자동 재분류(상호 배타), title/summary 사용자 수정 보존(`title_source`/`summary_source`+원자적 조건부 UPDATE, plan-01/03/04 재사용 패턴).
- `CategoryService`/`CategoryController`(CRUD·시스템 카테고리 보호·중복 이름 거부) + `LinkCategoryService`(상호 배타 유지) + `LinkService.save` 확장(기본 카테고리 없음 연결) + `PATCH /api/links/{id}`(title/summary 수정) + V8 마이그레이션. 리뷰에서 버그 0건(plan-04와 대조적).
- **PR #27 CI가 계속 fail했던 이유가 있었음**: `plan/05-categories`(계약+테스트만, red)는 구현이 없어 CI가 절대 통과할 수 없는 게 정상 — #29(구현, #27 커밋 포함)의 base를 `main`으로 재타겟해 한 번에 머지하는 방식으로 해결. 앞으로도 "계약 테스트만 있는 plan 브랜치"에 CI green을 기대하면 안 된다는 걸 기록해둔다.
- 로드맵에 "실험 스프린트"(plan-05~06 사이) 추가: exp-06(SSRF 회귀)·exp-03(장애 주입)은 로컬, exp-01→02→05→07(before/after)은 OCI 1인스턴스 실험 배포 하나로 진행. `기술스택.md` 2-15에 모노레포 유지 근거(ADR-001 연장) 명시.

## exp-06(SSRF 안전성 회귀) — 완료, 실험이 아니라 긴급 보안 수정으로 전환 (PR #31)
- corpus 설계 중 `LinkFetchServiceImpl` 재검토로 **확정된 보안 결함 2건**을 코드만으로 발견: ① DNS rebinding(TOCTOU) — 검증용 DNS 조회와 실제 연결용 DNS 조회가 분리돼 있어 두 조회가 다른 답을 주면(공인 IP→사설 IP) 뚫림. ② IPv6 미검사 — `isBlockedAddress`가 4바이트(IPv4)만 보고 IPv6(16바이트)는 무조건 통과.
- 실험 대신 즉시 수정: `SafeDnsResolver`(신규) + `PinnedDnsResolver`(내부 클래스, hostname당 `fetchOnce` 1회 조회를 `ThreadLocal`로 캐싱해 검증·연결이 항상 같은 조회 결과를 쓰게 구조적으로 통합) + IPv6 loopback·link-local·unique-local(`fc00::/7`) 차단 + 미확인 주소 길이 fail-closed.
- `LinkFetchSsrfAdvancedContractTest`(영구 계약 테스트, 4건 red→green 확인 + 다중 hop redirect 체인 1건은 이미 안전해서 처음부터 green) — exp-06이 원래 하려던 "corpus 기반 회귀 검증"을 이 계약 테스트가 영구히 담당.
- `mistake-ledger`에 `adr-implementation-drift` 2회째 기록(승격 후보, 3회째면 skill/hook 승격).

## 미완료
- **실험 스프린트 남은 것** — exp-03(장애 주입·복구, 로컬, plan-06 착수 전 아무 때나)과 exp-01/02/05/07(OCI 1인스턴스 배포 필요)이 plan/README.md에 계획만 돼 있고 실행된 게 없음.

## 다음 시작점
- **exp-03(장애 주입·복구) 착수** — 로컬, Toxiproxy/WireMock으로 URL timeout·429·OpenAI 5xx·worker 중단+lease 만료 복구·DB connection 제한 재현. plan-06 착수 전에 끝낼 것.
- 그다음 OCI에 1인스턴스 실험용 최소 배포 → exp-01(DB pool)→exp-02(worker capacity)→exp-05(AI latency·비용 메커닉) → worker profile 조건부 설정 구현(기술스택.md 2-15 "미해결" 항목) → 2인스턴스 전환 → exp-07(인스턴스 격리 before/after). 이 결과로 알파 배포 위상(1 vs 2인스턴스) 확정.
- 그 다음이 **plan-06-archive-and-search**(의존: 04,05) — 위험 로직 "✔ tenant filter 서버 강제" 플래그 있음, 착수 전 plan/README.md 절차 따를 것.

## 금지
- `src/test/**/contract/**`(계약 테스트) 수정 금지 — Codex뿐 아니라 자동화 전반.
- 학습 아티클에 최종 결정 문구를 쓰지 않는다 — 결정은 ADR에만.
- **위험 로직·기술 선택 시 Claude가 선택지부터 제시하지 않는다** — 사용자 가치·품질 기준을 먼저 묻는다(2026-07-26 절차 개정).

---
갱신: 2026-07-27 · 브랜치: `main`(plan-01/02/03 전체 머지 완료 상태)
