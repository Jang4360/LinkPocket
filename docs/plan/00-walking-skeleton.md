---
기능: Walking Skeleton (Gradle+Spring+Neon 파이프라인 관통)
관련 축: A(하네스) · C(DB) · G(운영) / 주차: 0
상태: **승인됨 (2026-07-15) — 계약 테스트 작성 → Codex 구현 대기**
---

## 승인된 설계 결정 (게이트 ①)
- **단일 Gradle 모듈** (Kotlin DSL) + 패키지로 경계. 모듈 분리는 신호 나면(adr-001).
- health는 **Spring Boot Actuator** (`/actuator/health`).
- **Neon은 아직 없음 → 배포 시점에 연결.** 이번 slice의 DB 검증은 **Testcontainers Postgres로만** 한다.

## 목적 (한 줄)
얇은 수직 슬라이스로 **빌드→테스트→CI를 한 번 관통**시켜, 이후 모든 기능이 올라탈 뼈대와 게이트(`verify.sh`의 테스트 단계)를 실제로 켠다.

## 범위
- **포함:** 단일 Gradle 모듈(Kotlin DSL) + 패키지 경계, Spring Boot 3.4, **Actuator** health, Flyway V1, Testcontainers Postgres 통합테스트 1개, `verify.sh`에서 `./gradlew test` 실제 실행, CI(`verify.yml`)에서 green.
- **제외:** 도메인 기능(Link·Auth·Category 등), 실제 비즈니스 엔드포인트, OpenAPI 전체 — 이건 plan-01+에서. **실 Neon 접속**(배포 시점).

## Acceptance Criteria (수용 기준)
- [ ] `./gradlew build` 성공하고, `./scripts/verify.sh`가 **실제로** `./gradlew test`를 실행해 green이다 (더 이상 "gradle 없음" no-op 아님).
- [ ] `GET /actuator/health` → `200`, 본문 `{"status":"UP"}` (Spring Boot Actuator).
- [ ] Flyway 마이그레이션 `V1__baseline.sql`이 앱 기동 시 적용된다 (빈 baseline 또는 최소 테이블 1개).
- [ ] **Testcontainers가 띄운 일회용 Postgres** 위에서 스프링 컨텍스트가 뜨고, DB 연결(`select 1`)과 `GET /health` 200을 검증하는 통합테스트가 있다.
- [ ] CI(`.github/workflows/verify.yml`)가 PR/push에서 `verify.sh`를 돌려 이 테스트를 green으로 통과시킨다.

## 불변식 (항상 참)
- `verify.sh`는 gradle 프로젝트가 존재하면 **테스트를 건너뛰지 않고 실제로 실행**한다.
- 통합테스트는 로컬/원격 개발 DB가 아니라 **Testcontainers가 띄운 일회용 Postgres**를 쓴다 (외부 상태 비의존, 재현 가능).
- 앱 설정에 **DB 접속 비밀·API 키를 커밋하지 않는다** (env/`.env`는 gitignore). 

## 실패 조건 (이렇게 되면 실패로 본다)
- gradle 프로젝트가 있는데 `verify.sh`가 테스트를 스킵하면 실패.
- 통합테스트가 Testcontainers 없이 개발자 로컬 DB에 의존하면 실패(다른 머신/CI에서 깨짐).
- Flyway 없이 스키마를 앱이 직접 생성(`ddl-auto=update` 등)하면 실패 — 마이그레이션은 Flyway가 단일 소스.
- 비밀 값이 소스/설정 파일에 하드코딩돼 커밋되면 실패.

## API 계약 (해당 시)
- `GET /actuator/health` → `200 application/json` `{"status":"UP"}`. (이후 실제 엔드포인트의 OpenAPI는 `architecture/openapi`에 축적 시작 — 이번 slice에선 health만.)

## 구현 계획 (task = 작은 PR)
1. **task-00a — 스캐폴딩 + health**: Gradle Kotlin DSL, Spring Boot 3.4, `GET /health`. `verify.sh`가 `./gradlew test`를 돌리도록(주석 해제). *DB 없이도 이 단계 green.*
2. **task-00b — DB 설정 + Flyway**: DataSource + Flyway(`V1__baseline.sql`), `ddl-auto=validate`(또는 none). 접속은 **Testcontainers로 검증**(실 Neon은 배포 시점 env 주입).
3. **task-00c — Testcontainers 통합테스트 + CI**: `@SpringBootTest` + `@Testcontainers` Postgres 컨테이너로 컨텍스트 기동·`/health`·`select 1` 검증. CI에서 이 테스트 실행 확인.

## 위험 로직 결정 (동시성·트랜잭션 경계 등)
- **없음** — 인프라 슬라이스라 동시성/트랜잭션 경계 결정이 없다. (다음 plan-01부터 세션 경계 인터뷰 시작.)

## 계약 테스트 (Claude 작성) — 이 slice의 특수성
- 원칙상 계약 테스트는 Claude가 `src/test/**/contract/**`에 작성한다. **단 walking-skeleton은 부트스트랩**이라 프로젝트 자체가 없으므로, 이 slice의 통합테스트(컨텍스트 기동 + `/health` + `select 1`)를 계약 테스트 위치에 함께 놓는다. **plan-01부터** "Claude가 계약 테스트를 먼저(red) 쓰고 Codex가 통과시킨다"는 순서를 엄격히 적용한다.

## 대조 기록 (SDD 증거 — 구현 중 채움)
- AI 계획 중 수정/거절한 것과 이유:
- spec↔구현 누락 건수 · 수정 turn 수:
- `verify.sh` 활성화 전후 게이트 동작 로그:
