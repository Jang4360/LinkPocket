# evidence — 이력서·포트폴리오로 옮길 수 있는 근거 (운영 후)

> 이 폴더는 기능 문서(`plan/`)도, 정책 문서(`operations/`)도, 실험 계획(`experiments/`)도 아니다.
> **"무엇을 실제로 했고, 무엇을 관측했고, 무엇은 아직 제안일 뿐인가"를 사실 기준으로 분리해 기록**하는 곳이다. 목적은 LinkPocket을 나중에 이력서·자소서·면접에서 **사실만으로** 말할 수 있게 하는 것.

## 왜 이 폴더가 따로 필요한가

`plan/`은 "무엇을 만들지"의 계약이고, `experiments/`는 "무엇을 측정할지"의 설계다. 둘 다 **결과가 나오기 전** 문서다. 결과가 실제로 나온 뒤 "이걸 성과로 써도 되는가"를 판정하는 계층이 없으면, 계획 단계 문구(가설·목표 수치)가 시간이 지나면서 슬그머니 "달성한 결과"처럼 읽히는 사고가 난다. 이 폴더가 그 경계를 명시적으로 지킨다.

## `Implemented` / `Observed` / `Proposed` 정의

| 상태 | 의미 | 예 |
|---|---|---|
| **Implemented** | 코드·계약 테스트·ADR로 존재한다. 아직 실제 트래픽에서 검증되지 않았다. | "SSRF 방어 로직을 구현했다"(계약 테스트 통과) |
| **Observed** | 실제 운영(알파 사용자 트래픽) 또는 통제된 실험(k6·장애 주입)에서 **측정된 결과**가 있다. raw artifact가 있다. | "부하 200 VU에서 p95 320ms, 커넥션 풀 40에서 포화 관측"(exp-01 raw 데이터) |
| **Proposed** | 아직 구현·측정 전. 가설이거나 다음에 할 일이다. | "HNSW 도입은 corpus가 10만 벡터를 넘으면 검토" |

**이력서·포트폴리오·자소서에는 `Observed`만 결과 문장으로 쓸 수 있다.** `Implemented`는 "만들었다"까지만 쓸 수 있고 성과 수치를 붙이지 않는다. `Proposed`는 이력서에 쓰지 않는다(면접에서 "다음 계획"으로는 말할 수 있되, 성과로 포장하지 않는다).

## 코드·ADR·실험·대시보드·사용자 피드백을 연결하는 규칙

각 주장(claim)은 아래를 갖춰야 `claim-ledger.md`에 등록된다:
- **코드/ADR 근거**: 실제로 그 동작을 구현한 파일 경로 또는 결정한 ADR 링크.
- **실험·운영 근거**(Observed로 올리려면 필수): raw artifact(`experiments/exp-NN/raw/`, 대시보드 스냅샷, k6 결과 JSON 등) 링크. 가공된 요약 수치만 있고 원본이 없으면 Observed로 인정하지 않는다.
- **조건**: 그 결과가 성립한 환경·표본 크기·기간. 조건 없이 "빨라졌다"는 문장은 안 쓴다.
- **보장 범위·한계**: 이 결과가 **아닌** 것. 과장 방지의 핵심 칸이다.

## 이 폴더가 갖는 문서

- [claim-ledger.md](claim-ledger.md) — "무엇을 주장할 수 있는가"의 단일 원장.
- [experience-card-template.md](experience-card-template.md) — 핵심 사건(장애·실험·릴리스) 하나가 끝날 때마다 채우는 서사 카드.

## 앞으로 생길 문서(착수 시점에 이 규칙을 따라 새로 작성)

아래는 아직 만들지 않았다 — 해당 작업이 실제로 시작될 때, **이 문서의 Implemented/Observed/Proposed 구분과 raw artifact 원칙을 그대로 따라** 새로 작성한다. 목록·트리거 조건은 각 홈 인덱스에 등록해둔다:
- 운영·제품 데이터 계약(`alpha-analytics-contract.md`, `alpha-feedback-loop.md`) → [operations/README.md](../operations/README.md) "앞으로 쌓는 것" 참고.
- 실험 문서(`exp-02`~`exp-05`) → [experiments/README.md](../experiments/README.md) "실험 목록" 참고.
- 릴리스·rollback 증거(`release-and-rollback-evidence.md`) → [operations/README.md](../operations/README.md) 참고.

> 원칙(모든 미래 문서에 적용): 실험·정책 문서는 가설·조건만 채우고 **결과는 실행 전 비워둔다.** 예시 수치를 실제 결과처럼 적지 않는다. 각 문서는 관련 ADR·plan·코드 경로·dashboard·raw artifact 링크 칸을 둔다. 포트폴리오용 문장(30초 소개·꼬리질문 등)은 결과가 나온 뒤 [experience-card-template.md](experience-card-template.md)에만 쓴다 — 실험·정책 문서 자체에는 쓰지 않는다.
