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

**실행 환경 원칙(2026-07-27):** 성능·용량 실험(exp-01·02·05·07)은 로컬 개발 머신이 실제 OCI ARM 8GB
인스턴스보다 자원이 넉넉해 포화점을 못 보고 거짓 확신을 줄 수 있다 — **반드시 실제 OCI 배포에서** 실행한다.
정확성 위주 실험(exp-03·06)은 하드웨어 사양과 무관해 로컬(Testcontainers)로 충분하다.
순서·시점은 [plan/README.md](../plan/README.md) "실험 스프린트" 참고 — exp-06·03이 먼저(로컬, 지금부터),
exp-01→02→05→07이 그다음(OCI, plan-05 머지 후~plan-06 착수 전 한 번에).

| 실험 | 실행 환경 | 상태 | 관련 축 |
|---|---|---|---|
| [exp-01 · 커넥션 풀 상한(부하로 DB 포화점)](exp-01-connection-pool-sizing/README.md) | **OCI**(1인스턴스) | 계획 | C(pool·transaction) + G(k6) |
| exp-02 · worker capacity·backpressure(worker 동시성+HTTP pool+domain별 제한, job claim·lease·재시도 값의 부하 검증) | **OCI**(1인스턴스, exp-01과 같은 배포) | 미작성(착수 전) | D(외부 수집) + E(비동기) + G(k6). exp-01과 DB pool 부분 중복 금지 — exp-01을 참조하고 worker claim·polling·HTTP pool·domain 제한만 다룬다. lease 30s·재시도 2회(ADR-012/014)는 재질문 대상이 아니라 **부하 하에서 그 값이 맞는지 검증하는 대상**이다 |
| exp-03 · fault injection·recovery(Toxiproxy/WireMock 장애 주입, timeout·retry·멱등성·worker 중단 복구) | 로컬 | 미작성(착수 전) | D(외부 수집) + E(비동기). 계획된 실험이라 postmortems/(실제 장애)와 다르다 |
| exp-04 · retrieval quality(golden set·Recall@5·MRR@10) | 알파 이후 | 미작성(착수 전, 우선순위 낮음) | F(검색 품질). **알파 사용자 데이터 확보 후 golden set 방식부터 정한다** — 지금 합성 데이터로 만들지 않는다 |
| exp-05 · AI latency·비용 메커닉(chunk 크기·overlap·모델 선택이 비용·지연에 주는 트레이드오프만) | **OCI**(1인스턴스, exp-01·02와 같은 배포) | 미작성(착수 전) | F+운영. **품질 평가(사용자 수정률 등)는 제외** — 실사용자 없이는 의미가 약해 알파 이후 AI 비용 정책 작업(plan/README.md "알파 종료 직후 최우선")과 함께 다룬다. 이 실험은 합성 corpus로 latency·token·비용 기계적 트레이드오프만 본다 |
| exp-06 · 수집기 SSRF 안전성 회귀(우회 corpus 기반) | 로컬 | 미작성(착수 전) | D(외부 수집) 보안. ADR-011이 이 프로젝트에서 가장 넓은 공격 표면이라 명시한 지점 — DNS rebinding·redirect 우회 시도 corpus로 기존 방어(plan-03)가 실제로 막는지 회귀 검증. 알파 사용자 데이터 불필요, 개발 중 아무 때나 가능 |
| exp-07 · API/Worker 2인스턴스 격리 검증(before/after) | **OCI**(1→2인스턴스 전환, before는 exp-01·02·05와 같은 배포 재사용) | 미작성(착수 전) | 아키텍처. [기술스택.md 2-15](../decisions/기술스택.md)의 "worker backlog가 API 응답에 안 새어나간다"는 가정을 실측 검증. **1인스턴스 baseline을 먼저 측정 → 같은 부하 시나리오로 2인스턴스 재측정 → 비교**(변수 하나만 바꾸는 이 문서의 공통 형식 그대로). 실제 OCI 배포가 필요해 로컬 실험이 아니다 |

**새 실험 문서를 착수할 때:** [evidence/README.md](../evidence/README.md)의 Implemented/Observed/Proposed 구분과 raw artifact 원칙을 그대로 따른다. 가설·조건만 먼저 채우고 결과는 실행 전 비워둔다.
