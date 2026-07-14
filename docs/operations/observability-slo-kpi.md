# 관측 가능성·SLO·제품 KPI

> 출처: `learning/cs-learning.md`에서 분리 (2-G OTel 스택·KPI, 2-F RAG CI 게이트)

## 1. OpenTelemetry 관측성 스택

```text
Spring Boot Actuator / Micrometer / OTel instrumentation
        ↓ OTLP
OpenTelemetry Collector
        ├─ Metrics → Prometheus 또는 Mimir
        ├─ Traces  → Tempo
        └─ Logs    → Loki
                     ↓
                   Grafana
```

- 애플리케이션은 특정 APM vendor exporter 대신 OTLP를 기본 경계로 삼는다.
- Collector에서 batch, retry, sampling, 민감정보 제거를 맡겨 애플리케이션과 backend를 분리한다.
- alpha 최소 구성은 `Micrometer metrics + 핵심 trace + Collector + Grafana dashboard`다. Loki/Mimir까지 한 번에 운영하지 않고 managed LGTM 또는 로컬 최소 구성의 비용·운영 복잡도를 ADR로 비교한다.
- `linkId`, `jobId`, `pipeline.stage`, `model`, `provider`, `outcome`은 유용하지만 `userId`, URL 전문, prompt 본문은 metric label로 넣지 않는다. cardinality와 개인정보 노출을 함께 막는다.
- Collector 자체의 dropped data, queue, export failure도 관측하고, Collector 장애가 비즈니스 요청을 막지 않게 비동기 export·bounded queue를 사용한다.

## 2. RED/USE·SLO·alert

| 학습·적용 | 내용 |
|---|---|
| RED/USE와 비즈니스 지표 | API rate/error/duration, pool/CPU/memory와 save→index 성공률·oldest job age를 함께 관측 |
| SLI/SLO와 alert | save API, index-ready, search, answer에 소수의 SLO 설정. alert는 임계값이 아니라 사용자 영향과 연결 |
| 저장→색인 추적 | requestId/linkId/jobId/traceId로 저장→색인 전체를 연결해 한 실패를 API 로그·job row·metric·trace에서 추적 |

## 3. 제품 KPI (기술 SLO와 섞지 않는다)

제품 KPI는 가용성 약속인 SLO와 같은 개념이 아니므로 한 표에 섞어 alert하지 않는다. 대신 같은 release·cohort·기간으로 연결해 "기술 문제가 제품 행동에 영향을 주었는가"를 분석한다.

| 지표 | 정의 초안 | 함께 볼 기술 지표·주의점 |
|---|---|---|
| saved-link reopen rate | 저장 후 7일 안에 원문 또는 상세를 다시 연 고유 링크 / 저장 완료 링크 | index-ready 시간, crawl 실패; 자동 preview는 open에서 제외 |
| first-save D7 retention | 첫 저장 사용자가 7일째 전후 정의된 window에 다시 저장·검색한 비율 | cohort 크기와 획득 경로를 함께 표기 |
| semantic search zero-result rate | filter 후 threshold를 넘는 결과가 0개인 query / 전체 유효 query | query 유형, tenant corpus 크기, retrieval latency |
| search success proxy | 검색 후 일정 시간 내 결과 클릭·저장 링크 재열람 비율 | 위치 bias가 있으므로 relevance 정답으로 단정하지 않음 |
| digest open/click rate | digest 전달 사용자 중 open 추정·링크/CTA 클릭 사용자 | open pixel은 privacy protection 영향이 커 보조 지표로만 사용하고 click-through를 주 판단에 사용 |
| digest unsubscribe/complaint | 발송 대비 수신거부·complaint 비율 | 콘텐츠 품질과 발송 빈도 판단, 즉시 suppression 확인 |
| crawl failure domain distribution | domain·failure taxonomy별 실패 URL 비율 | headless browser 도입과 domain 차단 우선순위 판단 |
| resurfacing rate | 연관 링크·digest 추천이 저장 이후 실제 재열람으로 이어진 비율 | 추천 노출 모수와 자연 유입을 분리 |

이벤트 이름·분모·중복 제거·bot/internal traffic 제외 기준을 analytics contract로 고정한다. 표본이 작으면 퍼센트만 제시하지 않고 분자/분모와 신뢰 구간 또는 원시 건수를 함께 공개한다.

## 4. RAG 품질을 CI에 넣는 방법

| CI 단계 | 평가 | 실패 정책 |
|---|---|---|
| PR hard gate | tenant leakage, 권한, schema, citation source 존재, 금지 tool 호출, 고정 corpus retrieval | 한 건이라도 실패하면 block |
| PR retrieval gate | pinned corpus·index·embedding version에서 Recall@5·MRR@10 | 절대 하한 미달 또는 승인된 baseline보다 5% 이상 상대 하락 시 block |
| PR generation smoke | temperature 0, 가능하면 seed·model snapshot 고정, 대표 질의 10~20개 | 구조·인용·거절 규칙만 block; 의미 점수는 advisory |
| nightly evaluation | 전체 golden set을 질의당 3회 실행, 규칙 기반+LLM judge+표본 human review | 평균뿐 아니라 최저·분산을 기록하고 2회 연속 회귀 시 merge/deploy 기준 재검토 |

temperature 0도 완전한 결정론을 보장하지 않는다고 가정한다. 결정론적 불변식은 hard gate로, 의미 품질은 반복 표본과 추세로 관리한다. model·prompt·corpus·index version과 평가 raw output을 모두 저장해야 회귀 원인을 재현할 수 있다.

> 관측 backend 선택(Grafana Cloud vs self-host LGTM)은 [decisions/기술스택.md](../decisions/기술스택.md) 2-13절, golden set 실험은 [experiments/](../experiments/) 참고.
