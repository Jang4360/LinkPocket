# LinkPocket 개발 루프 (계약 우선 · 2-에이전트 · 사람 게이트)

> **이 문서는 "어떻게 개발하는가"의 지배 문서다.** 사람과 두 AI가 이 루프를 공유한다.
> - **Claude Code** = 계획·설계·계약 테스트·리뷰
> - **Codex** = 구현 + 내부 단위 테스트
> - **사람** = 수용 기준·불변식 결정, 승인, 머지
>
> 목적 ① 나중에 내가 어떻게 진행했는지 판단하는 기준 ② AI가 이 플로우를 이해하고 벗어나지 않게.

## 이게 "loop engineering"인가?

**그렇다.** 정확히는 — *이 루프는 우리가 설계한 산출물*이고, *그 루프를 설계·측정·수렴시키는 행위*가 loop engineering이다 ([cs-learning A섹션](learning/cs-learning.md)). loop engineering은 "빨리 짜기"가 아니라 **"실패를 스스로 잡고 멈출 줄 아는 루프를 설계하기"** 다. 이 문서의 루프는 그중에서도 **계약 우선(contract-first) · 2-에이전트 · 사람 게이트** 변형이다.

## 철학적 근거 — loop engineering + harness engineering

이 개발 루프는 두 철학을 의도적으로 구현한 것이다. (근거: [reference/sources.md](reference/sources.md))

**loop engineering — 감이 아니라 수렴하는 루프를 설계한다.**
- `verify.sh`(계약 테스트)가 매 반복의 채점기(pass/fail 신호)다. "looks done"이 아니라 "check passed"에서 멈춘다.
- 종료 조건(green + Claude 리뷰), 예산·발산 감지가 루프를 멈추고 스스로 교정하게 한다.
- `iterations-to-green`·`drift`로 루프를 지표로 관리한다.

**harness engineering — 차이는 모델이 아니라 하네스(모델을 둘러싼 전부)에서 난다.**
- 하네스 구성요소: `plan`(SDD 계약) · 계약 테스트 · `verify.sh` · `hooks` · CI · `CLAUDE.md`/`AGENTS.md` 라우터 · 2에이전트 리뷰.
- 반복 워크플로우를 `skill`로 규격화해 **일정한 결과물**이 나오게 한다(아래 승격 규칙).

**두 철학의 접점:** 루프가 실패를 잡으면, 그 실패를 하네스(skill/hook)로 **흡수**해 다음 루프에선 아예 안 나게 한다. 이게 아래 [승격 규칙](#실수--가드레일-승격promotion-규칙)이다.

## 루프 (한 기능당 1 사이클)

```
[사람]   수용 기준·불변식 작성 (plan 산문)
[Claude] 계약 테스트(빨강) 작성 ─── 이게 Codex의 고정 타깃 ───┐
[사람]   계약 승인 ①                                          │
[Codex]  구현 + 내부 단위 테스트 추가, verify.sh 통과까지 반복 │
          · 계약 테스트/보호 경로 수정 시도 → hook 차단        │
          · 예산 초과 / 같은 실패 반복 / diff가 계획 밖 → 정지·에스컬레이트
[Claude] 리뷰: 게이트 통과했지만 불변식 실제로 지켰나 ────────┘
          · 계약이 틀렸으면 plan 수정하고 루프 재시작
[사람]   머지 승인 ③
```

## 소유권 (누가 무엇을)

| 산출물 | 소유 | 비고 |
|---|---|---|
| 수용 기준·불변식·실패 조건 (plan 산문) | **사람** | 최종 스펙 주인 |
| 계약 테스트 (블랙박스: 행동·불변식·API 계약) | **Claude** | Codex의 고정 타깃. 스펙에서 독립 작성 → blind spot 비상관 |
| 구현 코드 + 내부 단위 테스트 | **Codex** | 자기가 짜지 않은 계약에 수렴 |
| 계약 준수 리뷰 | **Claude** | 게이트 통과했어도 불변식 실제로 지켰나 |
| 게이트 실행 (`verify.sh`) | 자동 | 객관적 심판 |
| 승인 ①계약 ②에스컬레이션 ③머지 | **사람** | |

## 가드레일과 강제 — "AI가 hook을 풀거나 우회하지 않겠지?"에 대한 정직한 답

**문서만으로는 보장되지 않는다.** 문서는 협조적인 에이전트에게 *의도*를 알려줄 뿐이다. 잘못 동작하는(또는 혼란한) 에이전트는 표지판이 아니라 벽이 필요하다. 그래서 강제는 **AI 손이 닿기 어려운 곳**에 4겹으로 둔다.

### 보호 경로 (AI가 수정하면 안 되는 것)
- 계약 테스트 (예: `src/test/**/contract/**` 또는 `@ContractTest`)
- `.claude/settings.json` (hooks·권한)
- `scripts/verify.sh` (게이트)
- CI 워크플로우 설정

### 4겹 강제
1. **로컬 hook** — 보호 경로 write 시도를 차단하고 사람 승인을 요구한다.
2. **CI 독립 재검증** — AI가 통제하지 못하는 CI가 `verify.sh`를 다시 돌리고, **보호 경로 diff와 계약 테스트 약화 여부를 검사**한다. 로컬 hook을 우회해도 여기서 걸린다.
3. **브랜치 보호** — `main` 머지는 사람 승인 필수. AI가 직접 머지 못 한다.
4. **최소 권한** — AI에게 CI 설정 변경·`main` push 자격을 주지 않는다.

> 핵심: hook 하나는 뚫릴 수 있다. **뚫려도 CI·브랜치 보호가 독립적으로 다시 막는 것**이 설계다. "AI가 hook을 풀지 않겠지?"의 답은 *신뢰가 아니라 이 4겹*이다.

## 이 문서를 읽는 AI(Claude·Codex)를 위한 규칙

1. **보호 경로를 수정하지 마라.** 계약 테스트·`verify.sh`·`.claude/settings.json`·CI 설정은 사람만 바꾼다.
2. **게이트가 막으면 우회 경로를 찾지 마라.** 다른 파일로 돌아가거나 테스트를 무르게 고쳐 통과시키지 마라. **멈추고 사람에게 물어라.**
3. **가드레일이 틀렸다고 판단되면 직접 바꾸지 말고** 이유를 사람에게 제시하라. 변경은 사람만 승인한다.
4. **계약(불변식)이 잘못됐다고 생각되면** 구현을 비틀지 말고 plan 수정을 Claude·사람에게 제안하라.
5. **계획 밖 파일을 건드리지 마라.** diff는 해당 plan 범위 안에 둔다.

## 측정 (loop engineering 증거)

`iterations-to-green`(Codex 수렴 반복 수), `drift`(계획 밖 파일 수정 수), 보호 경로 차단 로그 건수, 계약 재작성 turn 수, 게이트는 통과했지만 Claude 리뷰에서 잡힌 불변식 위반 수 → [experiments/ai-limits-experiments.md](experiments/ai-limits-experiments.md)에 전후로 남긴다.

## 실수 → 가드레일 승격(promotion) 규칙

cs-learning A섹션의 "실패→가드레일 보강"을 임시방편이 아니라 **상시 루프**로 돌린다. 이게 harness engineering의 "규격화"다.

```
실수 발견(리뷰·게이트·postmortem)
  → operations/mistake-ledger.md 에 한 줄 기록 (카테고리 재사용)
  → 같은 카테고리 2회 = 승격 후보 / 3회 = 반드시 승격  (초기 가설, 조정 가능)
  → 유형 판정:  절차 반복 → skill(.claude/skills/)로 규격화 → 일정한 결과물
               불변 규칙 → hook(.claude/settings.json)로 강제 → 재발 원천 차단
               1회성     → 교정만, 원장에만 남김
  → 승격 생성은 항상 사람 승인 (behavior 변경이므로)
```

- 원장: [operations/mistake-ledger.md](operations/mistake-ledger.md) · 결정: [decisions/adr-005-mistake-promotion.md](decisions/adr-005-mistake-promotion.md)
- **자동 생성은 하지 않는다** — skill bloat·잘못된 추상화를 막기 위해 사람 승인을 둔다.
- 승격된 hook은 위 [4겹 강제](#4겹-강제)의 1겹이 되어 루프를 스스로 더 튼튼하게 만든다.

## 계획 단계의 필수 선행 — 위험 로직은 사람과 먼저 합의한다

동시성·트랜잭션 경계처럼 잘못된 기본값이 subtle 버그를 만드는 로직은 Claude가 **임의로 정하지 않는다.** 선택지와 견해를 사람에게 먼저 묻고, 합의 → `decisions/` ADR → `plan/` 순으로 문서화한다. 상세 절차는 [plan/README.md](plan/README.md).

## 이 문서를 어디서 참조하나

- 루트 `CLAUDE.md` / `AGENTS.md`(생성 시)가 이 문서를 첫 참조로 가리킨다 — 두 AI가 세션 시작 시 읽게.
- [docs/README.md](README.md)·[plan/README.md](plan/README.md)에서 링크.
