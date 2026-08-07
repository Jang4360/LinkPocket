# experiments — 실험 일지

폴더명: `exp-NN-제목/` (예: `exp-01-harness-context-map/`)
각 폴더 안에 `report.md` + 실행 script + raw 결과 파일을 함께 둔다.
**원본 수치를 지우지 않는다** — 가공된 퍼센트만 남기면 증거가 아니다.

## 공통 형식 (매트릭스 3절)

1. 같은 기능·acceptance criteria·환경을 고정한다
2. baseline 결과를 저장한다
3. 변수(가드레일) **하나만** 바꾼다
4. 같은 평가 스크립트로 재실행한다
5. 좋아진 지표 + 비용·시간·새로운 실패를 함께 적는다

## 실험 종류

- **AI 한계 6종** (환각·보안·성능·회귀·컨텍스트·의존성) — 최소 4개 완료 목표 → [ai-limits-experiments.md](ai-limits-experiments.md)
- **AI 실패→복구 서사** — 계획에 없던 실제 실패 최소 1건 (cs-learning A섹션 형식)
- **k6 부하** — workload별 시나리오, arrival rate·p95/p99·error rate 원본
- **검색·요약 평가** — golden set 기반 Recall@5·MRR@10, 모델/prompt/index 버전과 요약 표본 기록
- **조건부 기술 판정** — cs-learning 조건부 도입 표의 신호 측정 (Kafka 전환, cache 도입 등)

## 실험 목록

| 실험 | 상태 | 관련 축 |
|---|---|---|
| [exp-01 · 커넥션 풀 상한(부하로 DB 포화점)](exp-01-connection-pool-sizing/README.md) | 계획 | C(pool·transaction) + G(k6) |
| exp-02 · worker capacity·backpressure(job claim·lease·재시도 값의 부하 검증) | 미작성(착수 전) | E(비동기) + G(k6). exp-01과 DB pool 부분 중복 금지 — exp-01을 참조하고 worker claim·polling·AI 호출만 다룬다. lease 30s·재시도 2회(ADR-012/014)는 재질문 대상이 아니라 **부하 하에서 그 값이 맞는지 검증하는 대상**이다 |
| exp-03 · fault injection·recovery(Toxiproxy/WireMock 장애 주입) | 미작성(착수 전) | D(외부 수집) + E(비동기). 계획된 실험이라 postmortems/(실제 장애)와 다르다 |
| exp-04 · retrieval quality(golden set·Recall@5·MRR@10) | 미작성(착수 전, 우선순위 낮음) | F(검색 품질). **알파 사용자 데이터 확보 후 golden set 방식부터 정한다** — 지금 합성 데이터로 만들지 않는다 |
| exp-05 · AI cost·quality(generation 단위 비용·품질 계측) | 미작성(착수 전) | F+운영. plan/README.md의 "알파 종료 직후 최우선" AI 비용 작업의 측정 절반 |

**새 실험 문서를 착수할 때:** [evidence/README.md](../evidence/README.md)의 Implemented/Observed/Proposed 구분과 raw artifact 원칙을 그대로 따른다. 가설·조건만 먼저 채우고 결과는 실행 전 비워둔다.
