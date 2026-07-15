# AI 코딩 한계 6종 실험

> 출처: `learning/cs-learning.md`에서 분리 (3절 + 2-A 실패복구 슬롯)
> 원칙: 여섯 실험은 별도 토이 코드가 아니라 **LinkPocket의 실제 변경 과제**에 붙인다. 단, 취약하거나 느린 코드는 격리 branch/fixture에서만 만들고 운영 branch에 남기지 않는다.
> 목표치: 6종 중 **최소 4개**를 동일 조건 전후 실험으로 남긴다.

## 6종 실험

| AI 한계 | LinkPocket 실험 | 가드레일·개선 | 비교 지표 |
|---|---|---|---|
| 환각 | 존재하지 않는 parser/API·잘못된 상태 전이를 AI가 제안하는지 관찰 | OpenAPI·schema·type system·contract/integration test, 문서 출처 요구 | compile/test 실패, 가짜 API 수, 수정 turn |
| 보안 취약점 | URL fetcher와 요약·검색 경로에 SSRF·간접 prompt injection·tenant leakage 공격 | OWASP 체크리스트, 서버 강제 권한 필터, secret/tool 격리, 보안 회귀 테스트 | 공격 성공률, 차단 단계, false positive |
| 성능 무지 | archive N+1, 전체 메모리 로딩, 과도한 동시 fetch, 비효율 chunk 비교 | k6, query plan, JFR/pool metric으로 병목을 먼저 특정 | p95/p99, SQL 수, allocation, pool wait, 429 |
| 회귀 버그 | parser 또는 상태 머신 수정 뒤 기존 URL 유형·재처리가 깨지는지 관찰 | TDD, golden fixture, integration/E2E, mutation test, CI | 회귀 검출률, CI 차단 수, escaped defect |
| 컨텍스트 한계 | 전체 repo를 던진 작업과 module context+spec+skill 작업 비교 | context map, ADR index, scoped instruction, progressive disclosure | token, turn, 소요 시간, 불필요 수정 파일 |
| 의존성 폭증 | AI가 parser·retry·utility 라이브러리를 새로 추가하려는 제안 수집 | 표준 API/기존 dependency 우선, allowlist, lockfile, SBOM·취약점 scan | dependency 수, CVE, image/build time, 기각 수 |

## 실험 공통 형식

```text
1. 같은 기능·acceptance criteria·환경을 고정한다.
2. baseline AI 작업 결과를 저장한다.
3. 가드레일 하나만 추가한다.
4. 같은 평가 스크립트로 다시 실행한다.
5. 좋아진 지표뿐 아니라 비용·시간·새로운 실패를 함께 적는다.
6. 프롬프트 전문보다 재현 절차와 원본 결과를 공개한다.
```

"Claude로 token을 아꼈다"가 아니라 다음처럼 쓴다.

> 링크 상태 API 변경에서 전체 repo context 대신 module map+spec+test skill을 사용하자 입력 token은 X% 감소했고, 수정 turn은 A→B, 관련 없는 파일 수정은 C→D로 줄었다. 단, 초기 context map 작성에 N분이 들었다.

각 실험은 `exp-NN-제목/` 폴더에 `report.md` + script + raw 결과로 남긴다. (형식은 [experiments/README.md](README.md) 참고)

## 계획에 없던 실제 AI 실패 → 복구 서사 (최소 1건)

계획된 6종 실험과 별도로 개발 중 실제 발생한 AI 실패를 최소 1건 남긴다.

```text
작업과 당시 spec
→ AI가 만든 잘못된 코드와 놓친 불변식
→ test/CI/review/운영 지표 중 어디서 발견했는가
→ 사용자·데이터에 미칠 수 있었던 영향
→ 즉시 수정
→ spec/test/hook/skill 중 무엇을 바꿔 재발을 막았는가
→ 같은 유형을 다시 실행한 검증 결과
```

좋은 예시는 "AI가 틀렸다"가 아니라 `상태 전이 불변식이 spec에 없었기 때문에 AI가 완료 작업을 재실행했고, 멱등성 test와 skill checklist를 추가한 뒤 동일 mutation을 CI가 차단했다`처럼 **내 가드레일의 결함과 개선**까지 드러내는 기록이다.
