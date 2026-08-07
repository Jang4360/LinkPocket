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

## 미완료
- 없음 — plan-01~04 전부 완료·머지, 문서 개정 PR도 머지 완료(2026-07-27 기준).

## 다음 시작점
- **로드맵상 다음은 plan-05-categories**(카테고리 CRUD·다중 분류·제목/요약/분류 보정+재색인, 의존: 02). 위험 로직 플래그 있음("✔ 삭제=연결해제·보정 no-overwrite") — 착수 전 plan/README.md 절차대로 Claude가 먼저 질문(선택지 없이)부터 시작할 것.

## 금지
- `src/test/**/contract/**`(계약 테스트) 수정 금지 — Codex뿐 아니라 자동화 전반.
- 학습 아티클에 최종 결정 문구를 쓰지 않는다 — 결정은 ADR에만.
- **위험 로직·기술 선택 시 Claude가 선택지부터 제시하지 않는다** — 사용자 가치·품질 기준을 먼저 묻는다(2026-07-26 절차 개정).

---
갱신: 2026-07-27 · 브랜치: `main`(plan-01/02/03 전체 머지 완료 상태)
