# 세션 상태

> 다음 세션(사람이든 Claude·Codex든)이 `git log`·`git status`·PR 목록을 매번 재구성하지 않도록, 의미 있는 작업 단위가 끝날 때마다 이 파일을 갱신한다. **최신 상태만 남긴다** — 과거 이력은 git log가 이미 갖고 있으니 여기 쌓지 않는다.

## 완료
- plan-01(auth-google-oauth) 전체 완료·머지(PR #9). PKCE 코드교환·refresh rotation/reuse detection·웹 세션·공통 에러 프레임워크 전부 구현·리뷰·green. PR #6(계약 테스트만 있던 브랜치)은 #9로 흡수돼 닫음.
- 계약 테스트 인프라 버그 수정: `AbstractAuthContractTest`의 Postgres Testcontainer를 singleton 패턴으로 전환(다중 서브클래스 상속 시 컨테이너가 죽는 문제, `dcd6ea7`).
- `scripts/check-secrets.sh` 정규식 오탐 수정: `token`/`secret` 등 키워드가 camelCase 변수명(`refreshToken`) 중간에 낀 경우를 제외하도록 앵커링(`8bba327`) — macOS BSD grep·CI GNU grep 양쪽에서 직접 검증.
- plan-02(link-save-minimal) 착수: 위험 로직 합의 완료 → [ADR-010](../decisions/adr-010-link-idempotent-save.md) 작성(`(user_id, canonical_url)` unique constraint + `INSERT ON CONFLICT` 원자적 upsert, canonical URL 최소 정규화) → [plan/02-link-save-minimal.md](../plan/02-link-save-minimal.md) 초안 작성.
- plan/README.md 로드맵: 01 완료 처리, 02 승인 대기로 갱신.

## 결정과 근거
- 학습 아티클은 pre-hoc만 쓴다(post-hoc 폐지) — 결정은 항상 ADR에만. plan-02는 기술 선택 폭이 얕아(unique constraint가 사실상 유일한 정답) pre-hoc 아티클 생략 대상으로 판단.
- Link 멱등 저장은 select-then-insert(TOCTOU race)나 advisory lock(과설계) 대신 DB unique constraint + 원자적 upsert로 결정 — ADR-010 참고.
- canonical URL 정규화는 좁게 시작(scheme·trailing slash·www만) — 넓히는 건 다른 콘텐츠를 합칠 위험이 있어 되돌리기 어렵지만, 좁게 시작해 신호 생기면 넓히는 건 안전하다는 논리.

## 미완료
- plan-02.md·ADR-010이 초안 상태 — 사람 승인(계약 승인 ①) 전. 승인되면 Claude가 계약 테스트 작성.
- `/tmp/lp-wt-plan02` worktree(브랜치 `plan/02-link-save-minimal`)에 커밋 안 된 상태로 존재.

## 다음 시작점
- plan-02.md·ADR-010 승인 여부 확인 → 승인되면 Claude가 `src/test/**/contract/link/**`에 계약 테스트(빨강) 작성 → PR.
- 승인 전 수정이 필요하면 `/tmp/lp-wt-plan02`에서 계속 편집.

## 금지
- `src/test/**/contract/**`(계약 테스트) 수정 금지 — Codex뿐 아니라 자동화 전반.
- 학습 아티클에 최종 결정 문구를 쓰지 않는다 — 결정은 ADR에만.

---
갱신: 2026-07-25 · 브랜치: `plan/02-link-save-minimal`
