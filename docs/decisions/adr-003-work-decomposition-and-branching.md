# ADR-003: 작업 분해와 브랜치 전략 — Domain ⊃ Feature ⊃ Task → Trunk

- 날짜: 2026-07-15 / 상태: 확정
- **범주: 개발 프로세스 (작업 분해·브랜치)**
- 관련: [adr-001-modular-monolith.md](adr-001-modular-monolith.md)(Domain 축), [development-loop.md](../development-loop.md)

> 철학 정합: 작은 태스크 + always-green trunk = **짧고 자주 수렴하는 피드백 루프**(loop engineering). 근거는 [development-loop.md 철학적 근거](../development-loop.md) 참고.

## 상황

AI가 큰 변경을 한 번에 하면 리뷰·롤백이 어렵고 통합 지옥이 생긴다. **작업을 어떤 단위로 쪼개고 어디로 통합할지**를 정해야 한다. 이때 세 가지가 자주 혼동된다 — 코드 구조(도메인), 작업 단위(기능·태스크), 통합 지점(브랜치).

## 선택지 (통합 전략)

| 옵션 | 장점 | 단점 |
|---|---|---|
| Git Flow (long-lived branch) | 릴리스 격리 | 브랜치 오래 분기 → 통합 지옥, 항상 배포 불가 |
| GitHub Flow (feature branch → PR) | 단순 | 브랜치가 길어지면 여전히 분기 비용 |
| **Trunk-based (작은 변경 자주 머지)** | 항상 green·배포 가능, 분기 최소 | feature flag 관리 필요 |

## 결정

세 개는 **서로 다른 축**이며, 섞지 않는다.

```
[코드 구조 축]  Domain = modular monolith 패키지
                (archive / ingestion / retrieval / generation)   ← adr-001, 브랜치와 무관

[작업 분해 축]  Feature (= plan/NN 문서 하나, 예: 링크 저장)
                  └─ Task (feature를 쪼갠 작은 PR 하나)

[통합 축]       Trunk = main 브랜치 (단일 진실의 원천)
                Task(작은 PR)를 자주 머지, feature flag 뒤에서. squash + rebase, 항상 green 유지.
```

- **Trunk는 태스크·기능·도메인이 아니라 브랜치다.** Task는 trunk에 *들어가는 단위*, trunk는 그것들이 *쌓이는 곳*.
- 흐름: `Domain(패키지) ⊃ Feature(plan) ⊃ Task(작은 PR)` → **merge into** → `Trunk(main)`.
- 미완성 기능은 feature flag OFF로 trunk에 머지해도 사용자에게 안 보이게 한다.

## 트레이드오프

- feature flag 관리 부담이 생긴다. → 솔로·alpha 규모에선 간단한 env/config 토글로 충분(LaunchDarkly 불필요).
- 작은 PR을 자주 = 리뷰·머지 빈도가 는다. → 대신 통합 지옥과 롱리브 브랜치를 피하고 항상 배포 가능 상태를 유지.

## 재검토 조건

- 솔로라 브랜치 충돌이 거의 없으므로 flag가 과하면 최소화한다. 팀·다중 기여자가 생기면 branch protection·리뷰 규칙을 강화해 재검토한다.

## 근거 (외부)

- Atlassian·Aviator — Trunk-Based Development: 단일 trunk, 작은 변경 자주 통합, feature flag, 항상 green.
- GitHub Spec Kit — Spec → Plan → **Tasks** → Implement (작은 reviewable 단위).
- 링크: [reference/sources.md](../reference/sources.md)

## 면접 답변 요지

> "도메인(패키지 구조)·기능(plan)·태스크(작은 PR)·트렁크(main 브랜치)를 서로 다른 축으로 분리했다. 태스크를 feature flag 뒤에서 trunk에 자주 머지해 항상 배포 가능한 상태를 유지하는 트렁크 기반으로 갔다."
