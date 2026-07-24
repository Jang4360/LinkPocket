---
주제: dense·BM25·hybrid retrieval 결합 전략
관련 plan: [plan/06-archive-and-search.md](../../plan/README.md)
cs-learning 축: F. 검색·요약 품질과 AI 안전성 / sparse·BM25, dense, hybrid retrieval
작성일: 2026-07-24 / 상태: 초안
---

사용자가 "리액트 훅"을 검색한다. dense 임베딩 검색은 의미적으로 가까운 "vue composition api" 글까지 끌어오는데, 사용자가 원한 건 정확히 그 고유명사가 들어간 글이다. 반대로 "생산성을 높이는 방법"처럼 모호하게 검색하면 키워드 매칭(BM25)은 그 단어가 그대로 없는 좋은 글을 놓친다. 저장소는 이미 확정됐다(pgvector, [기술스택.md](../../decisions/기술스택.md) 2-4절) — 근데 pgvector는 dense만 되고, BM25는 Postgres full-text search를 별도로 붙여야 한다고 그 문서에 이미 적혀 있다. 문제는 "붙이는 방법"이다.

> hybrid retrieval은 두 검색을 같이 켜두는 기능이 아니라, 질의마다 어느 신호를 더 믿을지 정하는 판단이다.

## 이 스펙이 어떤 구조로 구현될 수 있는가

```
사용자 질의
  → [dense: pgvector cosine similarity]
  → [sparse: Postgres tsvector/tsquery BM25류 랭킹]
  → [결합 단계 — 이 아티클의 대상]
  → 최종 순위 → tenant filter(서버 강제, 불변조건) → 반환
```

## 후보 비교

| 선택지 | 좋아지는 것 | 비싸지는 것 | 이 프로젝트 규모 적합도 |
| --- | --- | --- | --- |
| dense만 사용 | 구현 가장 단순, 의미적 유사 검색에 강함 | 고유명사·정확 키워드 질의에서 zero-result 또는 엉뚱한 결과 | 중간 — 초기 MVP엔 충분, 고유명사 질의가 많으면 부족 |
| BM25만 사용 | 고유명사·정확 매칭에 강함, 구현·설명 쉬움 | 의미적 유사 질의(동의어·패러프레이즈)를 전혀 못 잡음 | 낮음 — "자연스러운 시맨틱 검색"이라는 plan-06 목표와 어긋남 |
| hybrid — 고정 가중 결합(RRF 등) | 두 신호를 항상 함께 반영, query-adaptive보다 구현 단순 | 모든 질의에 같은 가중치라 질의 유형별 최적은 아님 | 높음 — 다음 단계로 가장 자연스러운 출발점 |
| hybrid — query-adaptive(질의 특성으로 가중치 전환) | 질의 유형별 최적 성능 | 질의 사전 분류 로직·평가 비용 추가 | 낮음 — golden set으로 신호가 실측되기 전엔 과설계 |

## 어디서 무너지는가

hybrid를 "일단 둘 다 합치면 낫겠지"로 붙이면, 정규화 안 된 두 점수(코사인 유사도 vs BM25 스코어)를 그냥 더해 스케일이 큰 쪽이 항상 이기는 착시가 생긴다. 순위 기반 결합(Reciprocal Rank Fusion 등)이 이 스케일 문제를 피하는 흔한 완화책이다.

AI가 생성한 코드에서 자주 나는 실수: tenant filter를 결합 **이후** 애플리케이션 레벨에서 "걸러내기"로 처리하는 것. dense·sparse 각 쿼리 단계에서 서버가 `userId`를 강제하지 않으면, 결합 로직의 버그 하나가 tenant 경계 전체를 뚫는다 — [invariants.md](../../product/invariants.md) 전역 불변조건과 직결된다.

## 무엇을 보고 판단하는가

질의 유형별(고유명사 vs 의미 질의) `Recall@5`, `MRR@10`, `zero-result rate`. **"전체 평균 Recall"만 보면 이 문제는 완전히 숨는다** — dense가 의미질의를, BM25가 고유명사를 각각 잘 잡으면 평균은 둘 다 준수해 보이지만, 진짜 질문은 각 부류에서 hybrid가 단일 방식보다 실제로 나은가다.

## Claude 추천 · ADR로 넘길 질문

고정 가중치 RRF 기반 hybrid로 시작하길 추천한다. query-adaptive는 golden set(50~100개, cs-learning F축 P0)이 아직 없어 "무엇으로 질의를 분류할지"조차 데이터가 없다. RRF는 스케일 문제를 피하면서 구현이 단순해 이 프로젝트 규모에 맞는다.

**"질의 유형별 최적 성능"과 "구현·평가 단순성"은 동시에 얻을 수 없다** — query-adaptive가 이상적이지만 데이터 없이는 과설계다.

- 사람과 논의해 정할 것: golden set을 누가·언제 만들지, RRF의 순위 결합 상수를 임의로 정할지 초기 실험으로 튜닝할지.
- 최종 결정: (ADR 작성 후 여기 링크)
