# Claim Ledger — 무엇을 주장할 수 있는가

> [README.md](README.md)의 `Implemented`/`Observed`/`Proposed` 정의를 먼저 읽는다. **이력서·포트폴리오에는 `Observed` 행만** 성과 문장으로 옮길 수 있다.
> 초기에는 대상 예시만 등록하고 상태는 비워둔다 — 확인되지 않은 결과를 채우지 않는다.

| 주장 | 상태 | 코드/ADR 근거 | 실험·운영 근거 | 조건 | 보장 범위·한계 | 이력서 사용 가능 여부 |
|---|---|---|---|---|---|---|
| URL·fallback title 최소 보존 | Implemented | plan-02 계약 테스트, `LinkService.save` | — | — | 서버가 저장 요청을 **받은 이후**의 보장. 클라이언트가 요청 자체를 못 보낸 경우(오프라인)는 미포함(→ `02c-offline-save-queue` 예정) | 아니오(Observed 아님) |
| SSRF 방어와 redirect 재검증 | Implemented | ADR-011, plan-03 계약 테스트(SSRF 시나리오) | — | — | 계약 테스트 범위(사설 IP·redirect 우회)만 검증됨. 실제 공격 corpus로 회귀 검증한 적 없음(→ exp-03) | 아니오 |
| Job claim과 lease 회수 | Implemented | ADR-012, PR #24 계약 테스트(동시 claim·lease 만료 재claim) | — | — | 단위/통합 테스트 규모(수 개 job)에서만 검증. 실제 부하에서 lease 값(30s)이 적절한지는 미검증(→ exp-02) | 아니오 |
| AI 실패 시 Link 보존 | Implemented | ADR-014, invariants.md 전역 불변조건 | — | — | 계약 테스트 범위(재시도 가능/불가 각 케이스)만 | 아니오 |
| 검색 품질(Recall@5·MRR@10) | Proposed | dense-bm25-hybrid-retrieval 아티클(권고안) | — | golden set 없음(알파 데이터 필요) | plan-06 미착수 | 아니오 |
| 저장 후 관련 링크 추천 | Proposed | plan-07(미착수) | — | — | — | 아니오 |
| Disquiet 사용자 재열람 | Proposed | — | — | 알파 미출시 | — | 아니오 |
| AI 비용 제어 | Proposed | ai-cost-and-rate-limits.md(정책 초안) | — | 70/90/100% 임계값은 가설, 실측 없음 | 알파 종료 직후 최우선 작업으로 로드맵에 등록됨(plan/README.md) | 아니오 |
| 장애 감지·복구 | Proposed | — | — | exp-03 미작성 | — | 아니오 |
| 백업·복원 | Proposed | — | — | — | Neon 관리형 DB의 자체 백업에 의존 중, 별도 복원 리허설 없음 | 아니오 |

## 등록 규칙

- 새 주장을 추가할 때 상태는 기본 `Proposed`로 시작한다. 코드가 merge되면 `Implemented`로, raw artifact가 붙으면 `Observed`로 사람이 직접 승격한다(자동 승격 없음).
- `Observed`로 승격하려면 "실험·운영 근거" 칸에 raw artifact 링크가 **반드시** 있어야 한다. 요약 수치만 있고 원본이 없으면 반려.
- "보장 범위·한계"가 비어 있는 행은 미완성으로 본다.
