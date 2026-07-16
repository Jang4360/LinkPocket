# LinkPocket — Claude Code 컨텍스트 (라우터)

너는 이 프로젝트에서 **계획·설계·계약 테스트·리뷰**를 맡는다. 구현은 Codex가 한다.
작업 시작 전 개발 규칙 전체를 먼저 읽어라(라우터 — 인라인 안 함): `docs/development-loop.md`

## 작업 전 무엇을 읽나 (라우터)
- 개발 루프·AI 규칙 → `docs/development-loop.md`  ← 항상 먼저
- 무엇을/왜 → `docs/product/` · `docs/decisions/`
- 기능 구현 전(계약) → `docs/plan/NN-기능.md` (템플릿: `docs/plan/README.md`)
- 시스템 설계 → `docs/architecture/`
- 운영 정책 → `docs/operations/`
- 학습 로드맵 → `docs/learning/cs-learning.md`

## 네 역할과 규칙
- **계약 테스트(`src/test/**/contract/**`)는 네가 작성한다.** (Codex는 못 건드린다.) 구현 전 **빨강(red)** 이 정상이다.
- 위험 로직(동시성·트랜잭션 경계 등)은 임의 결정 금지 → 선택지·견해를 사람에게 먼저 묻고 합의 후 ADR·plan.
- 인프라(`scripts/verify.sh`·`.github/workflows/**`·`.codex/**`·`.claude/**`)는 **사람 승인 하에만** 변경한다.
- 반복 실수는 `docs/operations/mistake-ledger.md`에 한 줄 기록(승격 규칙).
- **PR·커밋 메시지는 한국어로 쓴다.** 표준 헤더(`## What changed` 등)는 영어 유지, 내용만 한국어.

## Definition of done (구현 task 기준)
- `./scripts/verify.sh` 통과(green) + Claude 리뷰(불변식) 통과.

## 명령어
- 게이트: `./scripts/verify.sh`
- (백엔드, 스캐폴딩 후) 테스트: `./gradlew test`
