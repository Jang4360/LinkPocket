# LinkPocket 핵심 불변조건 (1페이지 북극성)

> **이 문서는 상세 스펙이 아니라 앵커다.** 무엇을 만드는지는 [설계확정안](설계확정안.md)이,
> 왜 이 기술/구조인지는 [decisions/](../decisions/)가 단일 기준이다. 이 문서는 그 위에서
> **절대 어겨선 안 되는 것**만 한 페이지로 못박는다. 새 세션(사람이든 AI든)은 이 파일을 먼저 읽는다.

## 왜 만드는가 (한 줄)
저장한 링크를 다시 발견하게 한다 — 저장은 쉽게, 재발견은 AI가 돕는다.

## 성공 기준 (얕게)
- 저장 → 재탐색(검색·연관추천·다이제스트)이 실제로 이어진다.
- 핵심 API(저장·검색)의 SLO를 지킨다.
- 사용자 간 데이터가 절대 섞이지 않는다(tenant 격리).

## 비범위 (지금 안 하는 것)
- 답변 생성형 RAG (검색은 retrieval만, 설계확정안 4-4)
- 초기 협업·결제·다중 사용자 워크스페이스
- 링크 유형(linkKind) 분리 — 모든 저장 대상은 단일 `Link`

## 전역 불변조건 (모든 기능이 지켜야 하는 것)

| 불변조건 | 근거 문서 |
|---|---|
| **tenant 격리**: 서버가 세션에서 뽑은 `userId`만 신뢰, 클라이언트가 보낸 값은 절대 신뢰 안 함 | [ADR-007](../decisions/adr-007-domain-error-code-contract.md), [ADR-006](../decisions/adr-006-auth-session-architecture.md) |
| **복구 가능한 삭제**: 사용자 탈퇴·링크 삭제가 DB/vector/cache에 멱등하게 전파되고 orphan이 남지 않는다 | [operations/data-privacy-and-rights.md](../operations/data-privacy-and-rights.md) |
| **AI 실패가 저장 실패로 전파되지 않는다**: 수집·요약·임베딩이 실패해도 URL·제목은 보존된다 | [architecture/async-pipeline.md](../architecture/async-pipeline.md) |
| **문서=구현**: 계약(plan)이 코드보다 먼저 존재하고, 계약 테스트가 계약을 실행 가능한 형태로 고정한다 | [development-loop.md](../development-loop.md), [ADR-002](../decisions/adr-002-dev-methodology-sdd-tdd.md) |
| **비밀값은 절대 커밋되지 않는다** | [operations/mistake-ledger.md](../operations/mistake-ledger.md), `scripts/check-secrets.sh` |

## 문서 우선순위 (충돌 시)

```
invariants.md(이 문서, 절대 규칙)
  → 설계확정안.md(제품 범위)
  → decisions/ 최신 ADR (기술·구조)
  → architecture/ (런타임 설계)
  → plan/ (기능별 계약)
  → 구현 주석
```
충돌이 발견되면 **추측해서 진행하지 말고 멈춰서 사람에게 확인한다.**

## 다음 읽을 것
[docs/README.md](../README.md)의 "처음 읽는 순서"를 따라간다.
