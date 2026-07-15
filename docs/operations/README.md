# operations — 운영 정책·runbook·정기 회고 (개발 후)

배포 이후(6주차~)의 실무 증거. 기능 개발과 구분되는 운영 경험을 만든다.
**개별 장애 회고는 여기가 아니라 [postmortems/](../postmortems/)에 둔다.** 여기는 상시 정책·절차·정기 회고다.

## 상시 정책 (이미 있음)
- [data-privacy-and-rights.md](data-privacy-and-rights.md) — 개인정보·이용자 권리·크롤러
- [observability-slo-kpi.md](observability-slo-kpi.md) — OTel·SLO·제품 KPI·검색/요약 CI
- [ai-cost-and-rate-limits.md](ai-cost-and-rate-limits.md) — LLM 비용·cache·rate limit
- [mistake-ledger.md](mistake-ledger.md) — 반복 실수 누적 → skill/hook 승격 트리거 (하네스 자기개선)

## 앞으로 쌓는 것
| 문서 | 파일명 | 언제 |
|---|---|---|
| Runbook | `runbook-제목.md` | 반복 운영 절차가 생길 때 (배포·rollback, 삭제 전파, 수집 중단 요청, 이용자 권리 대응) |
| 정기 회고 | `retro-48h.md`, `retro-2w.md`, `retro-2m.md`, `retro-week-N.md` | 출시 후 48시간·2주·2개월·매주 (사용자 피드백, 실패 URL 유형, 비용, false alert, 품질 회귀 집계) |

> **정기 회고 vs postmortem:** 정기 회고는 사건과 무관한 **주기적 건강검진**(여기). postmortem은 **개별 장애 한 건**의 원인·재발 방지([postmortems/](../postmortems/)).

원칙: 실제 사용자 지표와 k6 합성 부하를 **섞지 않고** 명시적으로 구분해 기록한다.
