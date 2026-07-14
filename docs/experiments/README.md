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
- **RAG 평가** — golden set 기반 Recall@5·MRR@10, 모델/prompt/index 버전 기록
- **조건부 기술 판정** — cs-learning 조건부 도입 표의 신호 측정 (Kafka 전환, cache 도입 등)

## 실험 목록

| 실험 | 상태 | 관련 축 |
|---|---|---|
| [exp-01 · 커넥션 풀 상한(부하로 DB 포화점)](exp-01-connection-pool-sizing/README.md) | 계획 | C(pool·transaction) + G(k6) |
