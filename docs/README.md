# LinkPocket 문서 지도

> AI와 함께 개발하는 프로젝트다. **문서는 곧 AI의 컨텍스트이자 가드레일**이다.
> 그래서 폴더를 **개발 전 → 중 → 후** 순서로 두고, 각 단계에서 무엇을 근거로 삼아야 하는지 명확히 했다.
> 새 세션(사람이든 AI든)은 이 파일부터 읽는다.

## 왜 이 구조인가 (AI 협업 개발 기준)

[reference/sources.md](reference/sources.md)의 국내 사례에서 반복되는 형식을 따랐다.

- **카카오페이 spec-kit(SDD):** 코드 전에 명세를 먼저 → `plan/`
- **토스 harness:** AI에 짧은 컨텍스트·결정·규칙만 상시 제공 → `product/` `decisions/` `architecture/`
- **우아한형제들·올리브영 장애 대응기:** 운영과 장애를 자산으로 → `operations/` `postmortems/`

원칙: **많이 만들지 않는다.** 개발 전/중/후에 실제로 필요한 것만 두고, 나머지는 안 만든다.

## 처음 읽는 순서

1. [product/설계확정안.md](product/설계확정안.md) — **무엇을** 만드는가
2. [decisions/기술스택.md](decisions/기술스택.md) — **무엇으로·왜**
3. [learning/cs-learning.md](learning/cs-learning.md) — **어떻게 학습하며** 만드는가
4. [development-loop.md](development-loop.md) — **어떻게 개발하는가** (사람·Claude·Codex 공유 개발 루프 + AI가 지킬 규칙)

⚠️ 설계확정안은 2026-07-01 원본이라 일부(링크 3분류·Kafka·Qdrant)가 대체됨. 충돌 시 decisions·architecture가 우선. 상세는 설계확정안 상단 배너 참고.

---

## 폴더 구조 — 개발 전 / 중 / 후

```
docs/
├── README.md              이 문서 지도
├── development-loop.md    개발 루프 (계약우선·2에이전트·사람게이트) — "어떻게 개발하는가" + AI 규칙
├── release-checklist.md   출시 전 최종 점검 (전 폴더를 가로지르는 Definition of Done)
│
├─ 【개발 전】 정의하고 결정한다 ──────────────
├── product/        무엇을 만드는가 — 비전·문제·기능·스코프
│   └── 설계확정안.md
├── decisions/      왜 이 기술·구조인가 — ADR
│   ├── 기술스택.md · adr-001-modular-monolith.md · conditional-tech-adoption.md
├── plan/           기능별 명세+구현 계획 (SDD: 코드 전에 계약을 못박음)  ★신규
│   └── NN-기능.md (템플릿은 plan/README.md)
├── architecture/   시스템 설계도 — ERD·OpenAPI·상태머신·런타임 정책
│   └── auth-and-session.md · async-pipeline.md · database-and-infra.md
│
├─ 【개발 중】 만들며 배우고 측정한다 ──────────
├── learning/       학습 로드맵 + 아티클
│   ├── cs-learning.md (P0/P1 8축 + 주차별 실행표)
│   └── articles/ (정해진 범위 주제 심화 + 템플릿)
├── experiments/    측정·비교 실험 + raw 데이터
│   └── ai-limits-experiments.md
│
├─ 【개발 후】 운영하고 개선한다 ──────────────
├── operations/     운영 정책·runbook·정기 회고
│   └── data-privacy-and-rights.md · observability-slo-kpi.md · rag-cost-and-rate-limits.md
├── postmortems/    장애 회고·재발 방지 (개별 장애 한 건씩)  ★신규
│
└─ 【부속】 ─────────────────────────────────
    └── reference/  외부 자료 링크 + 프로젝트 목적(취업) 서사
        └── sources.md · career-narratives.md   (career 폴더는 여기로 흡수)
```

## 무엇을 어디에 기록하는가

| 이런 일이 생기면 | 여기에 남긴다 |
|---|---|
| **기능을 구현하기 전** 명세·계약을 잡는다 | `plan/NN-기능.md` ([SDD 템플릿](plan/README.md)) |
| 제품 비전·기능 스코프를 바꿨다 | `product/` |
| 기술을 선택/기각/변경했다 | `decisions/adr-NNN-제목.md` |
| 설계 산출물(ERD·API 명세·상태머신)을 만들었다 | `architecture/` |
| 학습한 주제 하나를 정리했다 (개념·적용·증거) | `learning/articles/주제.md` ([템플릿](learning/articles/README.md)) |
| 측정·비교 실험을 했다 (부하·RAG·AI 실험) | `experiments/exp-NN-제목/` |
| 반복 운영 절차가 생겼다 | `operations/runbook-제목.md` |
| 주기적 건강검진 회고를 한다 | `operations/retro-*.md` |
| **장애가 터졌다** | `postmortems/YYYY-MM-DD-제목.md` ([템플릿](postmortems/README.md)) |
| 참고한 외부 문서·공고·법령 링크가 생겼다 | `reference/sources.md` |
| 취업 서사·면접 답변·발행 글을 관리한다 | `reference/career-narratives.md` |
| 출시 전 전체 완료 조건을 점검한다 | `release-checklist.md` |

## 형식 규칙

- **plan(SDD)**: `plan/NN-기능.md`. 코드 전에 acceptance criteria·불변식·실패 조건·구현 계획을 쓰고 AI 계획과 대조한다. AI가 구현과 테스트를 동시에 지어내지 못하게 하는 계약.
- **ADR**: `decisions/adr-NNN-제목.md`. 상황 → 선택지(대안별 택한 핵심 이유) → 결정 → 트레이드오프 → **재검토 조건**.
- **학습 아티클**: 정해진 범위 주제 심화. 쓰기 전 [learning/cs-learning.md](learning/cs-learning.md)의 해당 축과 [learning/articles/README.md](learning/articles/README.md)의 말투·품질·템플릿을 읽는다.
- **postmortem**: `postmortems/YYYY-MM-DD-제목.md`. blameless — 타임라인 → 근본 원인 → 어디서 탐지 → 재발 방지(스펙/테스트/훅 변경).
- **문서 상단 메타**: 작성일, 상태(초안/확정/대체됨), 대체된 경우 무엇이 대체했는지 링크.

## 이 구조가 학습 로드맵과 연결되는 지점

- "취업용 최소 산출물"([reference/career-narratives.md](reference/career-narratives.md)) — ADR(`decisions/`), OpenAPI·상태머신(`architecture/`), 실험 raw(`experiments/`), runbook·회고(`operations/`), 장애 재발방지(`postmortems/`), 블로그 글(`reference/`).
- 각 스택 선택의 "면접 답변 요지"([decisions/기술스택.md](decisions/기술스택.md) 2절)는 나중에 `reference/career-narratives.md`로 모아 회사 유형별 서사와 매핑한다.
- 출시 전 전체 완료 조건은 [release-checklist.md](release-checklist.md)에서 한 번에 점검한다.
