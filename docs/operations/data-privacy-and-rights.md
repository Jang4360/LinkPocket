# 개인정보 처리·이용자 권리·책임 있는 크롤러

> 출처: `learning/cs-learning.md`에서 분리 (2-C 삭제·권리 파이프라인, 2-D 크롤러 정책)
> ⚠️ 이 문서의 법령·기한·동의 관련 체크리스트는 **법률 자문이 아니다.** 서비스 공개 시점의 최신 법령·KISA 안내와 실제 처리 데이터 기준으로 별도 확인한다.

## 1. 사용자 탈퇴·링크 삭제 파이프라인

```text
DELETE_REQUESTED
→ READ_BLOCKED
→ DB_CONTENT_DELETED
→ VECTOR_DELETED
→ CACHE_PURGED
→ COMPLETED | RETRY | MANUAL_REVIEW
```

- 요청 직후 tombstone/권한 차단으로 아카이브·검색·연관 추천에서 먼저 보이지 않게 한다.
- Qdrant/pgvector point는 `(tenantId, linkId, chunkId, modelVersion)`으로 추적 가능하게 만들고 DB 삭제 job과 연결한다.
- 단계별 멱등 키와 재시도 횟수를 두며, 완료 후 tenant/link filter로 orphan vector가 없는지 검증한다.
- 감사 로그에는 삭제 대상 본문·URL을 다시 남기지 않고 job ID, 저장소, 결과, 시각, 오류 코드만 보존한다.
- 외부 LLM provider에 보내는 개인정보를 최소화하고 provider별 보존·삭제 통제 범위를 개인정보처리방침과 ADR에 기록한다.

> 삭제 전파는 private 개발 단계에서는 P1이지만 **외부 사용자를 받기 전에는 P0로 승격**한다.

## 2. 이용자 권리와 개인정보 처리 운영

| 요청 | alpha 최소 대응 | 자동화 목표 |
|---|---|---|
| 열람·이동 | 본인 재인증 후 저장 링크·태그·상태·생성 데이터 export 요청을 수동 처리 | JSON/CSV export job과 완료 알림 |
| 정정 | 제목·태그·분류 수정과 원문 재수집 요청 경로 | 변경 이력과 vector/summary 재생성 범위 표시 |
| 처리정지 | 계정과 background job을 `SUSPENDED`로 전환하고 신규 crawl·요약·검색 색인·digest 중단 | 진행 중 job 취소/안전 종료와 재개 상태 머신 |
| 동의 철회·탈퇴 | OAuth 연결·모든 session/token family를 폐기한 뒤 삭제 pipeline 실행 | 저장소별 완료 증명과 실패 재처리 |
| 재가입 | 과거 탈퇴 계정의 token·data가 자동 복원되지 않는 새 identity/session 생성 | 삭제 미완료 계정의 재가입 충돌 테스트 |

- 공개 전 개인정보처리방침에 수집 항목, 목적, 보유 기간, 외부 AI/메일 provider와 국외 이전 여부, 파기 절차, 이용자 권리 요청 경로와 담당 연락처를 실제 구현과 맞춰 게시한다.
- alpha에서 UI 자동화가 늦어져도 이메일/문의 폼과 내부 runbook으로 요청을 받을 경로는 먼저 만든다. 요청 본인 확인, 접수·처리·결과 통지 기록에는 필요한 최소 정보만 남긴다.

## 3. 책임 있는 크롤러 운영 정책

- `LinkPocketBot/1.0 (+https://service.example/crawler-policy; contact@example.com)`처럼 식별 가능한 User-Agent를 사용한다.
- robots.txt는 domain별 TTL을 두고 cache하되, 규칙 변경·5xx·timeout의 처리 정책을 ADR로 정한다. RFC 9309에 없는 `Crawl-delay`는 표준으로 가정하지 않고 지원 사이트에 한해 보수적으로 해석한다.
- robots 허용이 저작물의 재배포 허락을 뜻하지는 않는다. 원문은 사용자 개인 검색 범위에서 최소한으로 저장하고 공개 화면·다이제스트에는 제목, 짧은 요약, 출처 링크를 우선한다.
- 권리자·사이트 운영자의 수집 중단 요청을 받을 공개 연락처와 URL/domain denylist를 운영한다. 요청이 승인되면 신규 수집 차단뿐 아니라 content, chunk, vector, cache, digest 후보까지 삭제 전파한다.
- JavaScript 렌더링은 기본 경로가 아니다. 정적 HTML·Open Graph·구조화 데이터로 충분하지 않은 비율을 먼저 측정하고 headless browser는 격리된 조건부 worker로만 검토한다. (도입 판정: [decisions/conditional-tech-adoption.md](../decisions/conditional-tech-adoption.md))

## 관련 참고

- 개인정보 파기 참고 링크는 [reference/sources.md](../reference/sources.md) 참고.
