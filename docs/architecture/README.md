# architecture — 시스템 설계 산출물

0주차 산출물(기술스택 문서 5절)이 여기에 들어온다.

- [ ] `openapi.yaml` — 저장·검색·다이제스트 API 초안 (API의 SSOT)
- [ ] `erd.md` — Link, Content, Chunk, Job (+ Feedback)
- [ ] `state-machine.md` — PENDING→FETCHED→CHUNKED→INDEXED, 삭제 파이프라인 DELETE_REQUESTED→…
- [ ] `system-diagram.md` — 아키텍처 다이어그램 v1 (콘텐츠형 단일 스코프 반영)

규칙: 구현과 어긋나면 문서를 먼저 고친다. OpenAPI는 CI에서 구현과 diff 검증하는 것이 목표(매트릭스 B섹션).
