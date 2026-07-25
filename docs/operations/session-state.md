# 세션 상태

> 다음 세션(사람이든 Claude·Codex든)이 `git log`·`git status`·PR 목록을 매번 재구성하지 않도록, 의미 있는 작업 단위가 끝날 때마다 이 파일을 갱신한다. **최신 상태만 남긴다** — 과거 이력은 git log가 이미 갖고 있으니 여기 쌓지 않는다.

## 완료
- plan-02(link-save-minimal) 전체 완료·머지(PR #12). unique constraint + `INSERT ON CONFLICT` 원자적 upsert, canonical URL 최소 정규화. PR #11(계약 테스트만)은 흡수돼 닫음.
- plan-03(safe-fetch-extract) 위험 로직 합의 완료 → [ADR-011](../decisions/adr-011-safe-fetch-ssrf-timeout-retry.md) 작성(SSRF 방어 깊이·timeout 구조·재시도/크기 제한·중복 fetch 방지) → [plan/03-safe-fetch-extract.md](../plan/03-safe-fetch-extract.md) 초안.
- **학습 아티클 정책 재수정**: 트리거를 "새 기술 도입" 위주에서 **"위험 로직(동시성·멱등성 등) 자체"를 핵심 트리거로** 확장. plan-02를 "unique constraint가 유일한 정답이라 아티클 불필요"로 판단했던 게 오판이었음 — 왜 다른 방법(낙관적 락 등)이 안 맞는지 설명하는 것 자체가 가치였다. `learning/articles/README.md`·`plan/README.md` 갱신.
- 새 개념 아티클 [idempotency-and-concurrency-control.md](../learning/articles/idempotency-and-concurrency-control.md) 작성 — unique constraint·비관적 락·낙관적 락·조건부 UPDATE·advisory lock을 plan-01/02/03 실제 코드로 설명. plan을 넘나드는 재사용 아티클(매 plan마다 새로 안 씀, 적용 사례만 추가).

## 결정과 근거
- 학습 아티클 트리거 2갈래: ①기술/라이브러리 선택(가벼움) ②위험 로직-메커니즘 선택(핵심, 이 프로젝트 무게중심). "선택지가 하나뿐"이어도 왜 그런지 설명하는 게 ②의 목적이라 생략 사유가 안 됨.
- plan-03 스코프: 공개 API 없음, `LinkFetchService.fetchAndExtract(linkId)` 서비스 메서드까지만 — 트리거(언제 호출하는지)는 job polling 인프라를 가진 plan-04 소관. (plan-03.md에 "사람 대조 필요"로 명시해둠, 아직 확인 안 됨)
- plan-03 중복 fetch 방지는 별도 멱등 테이블 대신 `link.status` 원자적 조건부 UPDATE(`PENDING→FETCHING`)로 — plan-01/02와 같은 패턴 재사용, plan-04의 job 인프라 설계와 겹치지 않게.

## 미완료
- plan-03.md·ADR-011이 초안 상태 — 사람 승인(계약 승인 ①) 전. 특히 "공개 API 없음" 스코프 판단 확인 필요.
- 승인되면 Claude가 fetch 계약 테스트(빨강) 작성 — WireMock으로 SSRF 시나리오(사설 IP·redirect 우회) 재현 필요.
- `/tmp/lp-wt-plan03` worktree(브랜치 `plan/03-safe-fetch-extract`)에 커밋 안 된 상태로 존재.

## 다음 시작점
- plan-03.md·ADR-011 승인 여부 확인(스코프 판단 포함) → 승인되면 Claude가 계약 테스트 작성.

## 금지
- `src/test/**/contract/**`(계약 테스트) 수정 금지 — Codex뿐 아니라 자동화 전반.
- 학습 아티클에 최종 결정 문구를 쓰지 않는다 — 결정은 ADR에만.

---
갱신: 2026-07-25 · 브랜치: `plan/03-safe-fetch-extract`
