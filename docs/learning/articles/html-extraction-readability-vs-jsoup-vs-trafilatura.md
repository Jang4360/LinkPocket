---
주제: HTML 본문 추출 라이브러리 — readability4j vs jsoup 직접 파싱 vs Trafilatura
관련 plan: [plan/03-safe-fetch-extract.md](../../plan/03-safe-fetch-extract.md)
cs-learning 축: D. 외부 URL 수집: 네트워크·보안 / 본문 추출
작성일: 2026-07-24 / 상태: 초안
---

fetch는 성공했다. SSRF 방어도 통과했다(Apache HttpClient5는 이미 확정된 선택 — [기술스택.md](../../decisions/기술스택.md) 2-5절). 그런데 저장된 링크의 "본문"을 열어 보면 네비게이션 메뉴, 쿠키 배너, "구독하기" 문구로 가득하다 — 이건 fetch 실패가 아니라 **추출 실패**다. `기술스택.md`도 이 항목만은 "아직 논의 필요"로 명시적으로 남겨 뒀다(4절: readability4j vs jsoup 직접 파싱 vs Trafilatura).

> 본문 추출은 HTML을 읽는 기술이 아니라, 페이지마다 "무엇이 진짜 콘텐츠인가"를 다시 추측하는 일이다.

## 이 스펙이 어떤 구조로 구현될 수 있는가

```
Fetch(HttpClient5, 확정) → raw HTML
  → [추출 단계 — 이 아티클의 대상]
  → Content(title, mainText, 구조 보존 여부는 후보마다 다름)
  → Chunk 생성(plan-04, 별도 아티클)
```

후보 A **readability4j**(Mozilla Readability의 JVM 포팅)는 DOM을 순회하며 텍스트 밀도·링크 밀도·태그 점수로 "본문 블록"을 채점한다. 후보 B **jsoup 직접 파싱**은 CSS selector(`article`, `main`, `.post-content` 등)로 우리가 직접 규칙을 짠다. 후보 C **Trafilatura**는 Python 학술 라이브러리로, 별도 프로세스(gRPC/HTTP 사이드카)로 호출해야 한다.

## 후보 비교

| 선택지 | 좋아지는 것 | 비싸지는 것 | 이 프로젝트 규모 적합도 |
| --- | --- | --- | --- |
| readability4j | 순수 JVM, 별도 프로세스 불필요, 뉴스·블로그류에 강함 | heuristic이라 SPA·비표준 마크업에서 정확도 하락 | 높음 — 인프라 추가 없이 바로 적용 |
| jsoup 직접 파싱 | 완전한 제어, HTML 파싱에 jsoup 자체는 어차피 필요해 의존성 재사용 | 사이트마다 규칙이 달라 "본문 추출 규칙"을 계속 손으로 유지해야 함 | 낮음 — 1인 개발 리소스를 규칙 유지에 계속 씀 |
| Trafilatura | 학술 벤치마크 최고 수준 정확도, 메타데이터(발행일 등) 풍부 | Python 별도 프로세스 → JVM 모놀리스에 이종 런타임 추가 | 낮음 — [conditional-tech-adoption.md](../../decisions/conditional-tech-adoption.md)의 "쓰기 위해 도입 안 한다" 원칙과 충돌 |

## 어디서 무너지는가

셋 다 JavaScript로 렌더링되는 SPA 콘텐츠는 raw HTML만으로 볼 수 없다 — 이건 별도 축이고, Playwright 도입은 이미 conditional-tech-adoption.md에 조건부 보류돼 있다("최근 실패 표본 100개 중 동적 렌더링 필요 10% 이상"이 도입 신호).

AI가 생성한 코드에서 자주 나는 실수: 추출 실패를 저장 실패로 취급하는 것. 하지만 [invariants.md](../../product/invariants.md)의 전역 불변조건("AI 실패가 저장 실패로 전파되지 않는다")과 정면으로 충돌한다. 추출이 완전히 실패해도 URL과 fallback title은 반드시 남아야 한다.

## 무엇을 보고 판단하는가

여러 도메인 표본(뉴스·블로그·기술문서·커머스)에서의 추출 성공률, 그리고 "본문처럼 보이지만 실제로 네비게이션/광고인" 오탐률. **"추출 실패율 0%"가 목표가 아니다** — 실패해도 저장(fallback title)이 유지되는지가 진짜 지표다. 착시: 실패율만 낮추려다 애매한 콘텐츠를 억지로 "본문"으로 판정하면 검색 품질(F축)이 조용히 나빠진다.

## Claude 추천 · ADR로 넘길 질문

readability4j로 시작하는 걸 추천한다. 별도 프로세스 없이 JVM 안에서 처리돼 conditional-tech-adoption.md 원칙에 맞고, 실패해도 fallback title 불변식만 지키면 위험이 낮다. Trafilatura는 실측 추출 정확도가 명백히 부족하다고 판명될 때 도입 신호로 남긴다.

**"추출 정확도"와 "런타임 단순성"은 동시에 최대화할 수 없다** — Trafilatura가 정확도는 높지만 이종 런타임 비용을 치른다.

- 사람과 논의해 정할 것: 추출 실패율 허용 기준(몇 %부터 문제로 볼지), readability4j 실측 실패율이 얼마 이상이면 Trafilatura를 재고할지의 임계값.
- 최종 결정: (ADR 작성 후 여기 링크)
