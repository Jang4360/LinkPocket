# ADR-005: 실패 → 가드레일 승격(promotion) 규칙

- 날짜: 2026-07-15 / 상태: 확정
- **범주: 개발 프로세스 (하네스 자기개선)**
- 관련: [development-loop.md](../development-loop.md), [operations/mistake-ledger.md](../operations/mistake-ledger.md), [learning/cs-learning.md](../learning/cs-learning.md) A섹션

> 철학 정합: 루프가 잡은 실패를 하네스(skill/hook)로 흡수해 다음 루프에선 안 나게 한다 — **loop engineering(수렴)과 harness engineering(규격화)의 접점**.

## 상황

AI는 비결정적이라 같은 유형의 실수가 재발한다. 매번 사람이 리뷰에서 잡으면 확장되지 않는다. cs-learning A섹션의 "실패→가드레일 보강"을 *임시방편*이 아니라 **상시 루프**로 만들어야 한다. 관건은 "반복"을 어떻게 감지하고, 무엇으로 규격화하느냐다.

## 선택지

| 옵션 | 장점 | 단점 |
|---|---|---|
| 매번 수동 교정 | 단순 | 확장 안 됨, 같은 실수 무한 반복 |
| 전부 선제 hook/skill | 강력 | 과설계, 안 일어날 것까지 방어 |
| AI가 skill 자동 생성 | 자동 | skill bloat·잘못된 추상화 → 하네스 오염 |
| **관찰 기반 승격(promotion)** | 실측 신호로만 규격화 | 원장·임계값 관리 필요 |

## 결정

**반복 실수를 원장에 누적하고, 임계값을 넘으면 사람 승인 하에 skill/hook으로 승격한다.**

```
실수 발견(리뷰·게이트·postmortem)
  → operations/mistake-ledger.md 한 줄 기록 (카테고리 재사용)
  → 같은 카테고리 2회 = 승격 후보 / 3회 = 반드시 승격
  → 유형 판정:  절차 반복 → skill(.claude/skills/)
               불변 규칙 → hook(.claude/settings.json)
               1회성     → 교정만
  → 승격 생성은 항상 사람 승인 (behavior 변경이므로)
```

- **자동 생성은 하지 않는다.** skill은 큐레이션·description 품질·"참조 한 단계"가 중요(Anthropic skill 가이드). 자동화하면 라우팅이 망가진다.
- 승격의 결과물(skill/hook)은 [4겹 강제](../development-loop.md)의 1겹(hook)이나 하네스 skill이 되어 다음 루프를 더 튼튼하게 만든다.

## 트레이드오프

- 원장 관리 소액 비용, 임계값(2/3)은 자의적인 초기 가설이다. → 아래 재검토 조건으로 조정.
- skill/hook이 늘면 하네스가 무거워질 수 있다. → 승격만 하고 정기적으로 프루닝(안 쓰는 skill 제거).

## 재검토 조건

- 승격이 너무 잦아 노이즈가 되면 임계값을 올린다(3→후보, 4→필수).
- 같은 실수가 승격 전에 사고(장애·데이터 문제)로 번지면 임계값을 내리거나 즉시 승격한다.
- skill 라우팅이 부정확해지면 skill을 통합·프루닝한다.

## 근거 (외부)

- Anthropic — 반복 절차는 skill로, 매번 일어나야 하는 것은 hook으로(결정적). skill은 큐레이션 필요.
- 하네스 엔지니어링 — "반복 워크플로우를 skill로 규격화해 일정한 결과물을 낸다."
- 링크: [reference/sources.md](../reference/sources.md)

## 면접 답변 요지

> "AI가 같은 실수를 반복하는 걸 사람이 매번 잡는 대신, 실수를 원장에 누적하다 임계값을 넘으면 skill이나 hook으로 규격화해 재발을 원천 차단하는 자기개선 루프를 설계했다. 자동 생성은 skill bloat 때문에 피하고 사람 승인을 뒀다."
