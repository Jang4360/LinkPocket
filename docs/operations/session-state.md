# 세션 상태

> 다음 세션(사람이든 Claude·Codex든)이 `git log`·`git status`·PR 목록을 매번 재구성하지 않도록, 의미 있는 작업 단위가 끝날 때마다 이 파일을 갱신한다. **최신 상태만 남긴다** — 과거 이력은 git log가 이미 갖고 있으니 여기 쌓지 않는다.

## 완료
- H축 학습 아티클 1개 작성(`refresh-token-rotation-reuse-detection.md`) — ADR-006 rotation·reuse detection을 계약 테스트 근거로 정리. 단, 이후 정책 변경으로 이런 post-hoc 형식은 더 안 씀(아래 "결정과 근거" 참고).
- Codex 진행 상황 확인: plan-01 task-01a(에러 프레임워크)·01b(웹 OAuth·`/api/me`·`/api/logout`) 완료, task-01c(PKCE 코드교환)·01d(refresh rotation) 미착수.
- task-01c·01d 구현 핸드오프 작성(device_session/refresh_token 2테이블 상태머신 설계 포함) — Codex에게 전달, 아직 실행 확인 전.
- ADR-009 외부 프레임워크 대조표를 새 하네스 노트 기준으로 교체(`docs/adr-009-ai-harness` 브랜치).
- 하네스 긴급 보완 3건: STOP CONDITIONS에 프롬프트 인젝션 항목 추가, `operations/review-quality-axes.md` 신설, 이 파일(`session-state.md`) 신설 + 라우터(CLAUDE.md/AGENTS.md) 연결.
- 학습 아티클 정책 변경: post-hoc(구현 후 되짚기) 폐지, pre-hoc(ADR 결정 전 기술 비교)만 사용하기로 확정. `plan/README.md`·`learning/articles/README.md`에 트리거·템플릿 반영.
- pre-hoc 학습 아티클 3개 작성: plan-03(safe-fetch-extract)·plan-04(async-ai-pipeline)·plan-06(archive-and-search).

## 결정과 근거
- 학습 아티클은 pre-hoc만 쓴다 — post-hoc과 겹치면 같은 내용이 두 번 적혀 drift 위험. 최종 결정은 항상 ADR에서만 확인한다. (사용자 승인)
- refresh rotation의 family 폐기는 `device_session`(family 단위 생사) + `refresh_token`(개별 소비 여부) 2테이블로 분리해야 세 번째 계약 테스트(family 폐기 후 미사용 토큰도 invalid)를 만족한다 — Codex 핸드오프에 이 상태머신을 명시.
- 기술 선택 아티클과 위험 로직 트리거는 별도 축이다 — 전자는 "여러 정답 후보 중 선택", 후자는 "안전하게 만드는 법". 트리거 목록도 분리해 문서화.

## 미완료
- Codex가 task-01c·01d를 아직 시작 안 함(핸드오프만 전달됨). `feat/01-auth-google-oauth`는 여전히 uncommitted 상태.
- `docs/adr-009-ai-harness` 브랜치의 이번 커밋들을 push하고 PR #8에서 사람 리뷰 대기.

## 다음 시작점
- `feat/01-auth-google-oauth` 브랜치: task-01c·01d 핸드오프 실행 여부 확인부터. 커밋 안 된 상태이므로 먼저 WIP 커밋 권장(task-01a·01b 유실 방지).
- `docs/adr-009-ai-harness` 브랜치: push 후 PR #8 리뷰·머지 확인.
- plan-02(link-save-minimal) 착수 시점에 이번에 확정한 "기술 선택 아티클" 절차가 처음 정식 적용됨(단, 02는 필요도 낮음으로 판단돼 아티클 생략 대상).

## 금지
- `src/test/**/contract/**`(계약 테스트) 수정 금지 — Codex뿐 아니라 자동화 전반.
- Codex가 작업 중인 `feat/01-auth-google-oauth` 워킹 디렉토리의 uncommitted 변경을 임의로 커밋·리셋하지 않는다.
- 학습 아티클에 최종 결정 문구를 쓰지 않는다 — 결정은 ADR에만.

---
갱신: 2026-07-24 · 브랜치: `docs/adr-009-ai-harness`
