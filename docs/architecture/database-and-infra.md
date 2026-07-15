# DB 모델·트랜잭션·Neon 운용 제약

> 출처: `learning/cs-learning.md`에서 분리 (2-C DB 모델·Neon 제약)
> 전제: Neon PostgreSQL + pgvector로 벡터·관계 데이터를 하나의 저장소에 통합 ([decisions/기술스택.md](../decisions/기술스택.md) 2-4절).

## 1. 모델·트랜잭션 원칙

- 정규화·snapshot/reference·versioning: Link, Category, LinkCategory, Content, Chunk, Job, Digest, OpenEvent를 분리하고 parser/model version을 보존한다.
- Link는 단일 저장 단위다. 링크 유형을 MVP 도메인 모델로 두지 않고, 사용자가 관리하는 Category와 N:M으로 연결한다.
- URL·canonical URL·fallback title은 Link 저장 성공의 최소 보장이고, OG 설명·본문·AI 요약·임베딩은 nullable한 후속 결과다. 사용자가 수정한 title/summary는 출처와 함께 보존한다.
- unique constraint·idempotent write: 사용자+canonical URL, content hash, job idempotency key의 책임을 구분한다.
- transaction 격리·lock: 여러 worker의 job claim(`SKIP LOCKED`), 사용자 삭제와 vector 삭제 전파.
- B-Tree·복합 인덱스·selectivity: 사용자별 최신 링크, 상태별 작업, 재처리·digest 후보 조회.
- keyset pagination: `(user_id, created_at, id)` 기반 archive pagination.
- N+1과 fetch 전략: 링크 목록에서 category·summary·status를 가져오는 쿼리 수 제한.
- connection pool·transaction duration: 외부 HTTP·LLM 호출을 transaction 밖으로 빼고 Hikari acquire/usage를 관측한다.
- migration·rollback: model/parser version 컬럼과 상태 enum을 expand/contract 방식으로 변경한다.

> 검증 증거(EXPLAIN ANALYZE, 데이터 크기별 p95 등)는 데이터 모델 학습 항목([learning/cs-learning.md](../learning/cs-learning.md) C섹션)과 연결.
> MySQL Workbench는 도구일 뿐 핵심 역량이 아니다. 운영 DB가 PostgreSQL이면 `EXPLAIN (ANALYZE, BUFFERS)`와 `pg_stat_statements`를 사용한다.

## 2. Neon PostgreSQL 운용 제약

- application traffic은 pooled endpoint, Flyway migration·pg_dump·logical replication/Debezium처럼 session/replication 동작이 필요한 작업은 direct endpoint 사용 여부를 공식 문서와 도구별로 확인한다.
- Neon PgBouncer는 transaction mode이므로 `SET/RESET`, session advisory lock, `LISTEN`, 일부 temp table 같은 session state를 transaction 밖까지 유지한다고 가정하지 않는다.
- 최신 Neon은 PgBouncer를 통한 **protocol-level prepared statement를 지원**한다. SQL-level `PREPARE/DEALLOCATE`와 session 상태 제약을 "prepared statement 전체 미지원"으로 잘못 일반화하지 않는다.
- Hikari와 PgBouncer의 이중 pooling에서 애플리케이션 pool을 크게 잡지 않는다. Hikari active/pending과 Neon pooler client/server connection을 함께 보고 작은 baseline에서 조정한다.
- scale-to-zero 뒤 첫 연결 지연과 기존 idle connection 종료를 cold-start workload로 따로 측정한다. keepalive로 suspend를 무조건 막기보다 비용 목표, 첫 요청 SLO, 재연결 정책의 trade-off를 ADR로 남긴다.

## 3. 삭제 전파

사용자 탈퇴·링크 삭제 시 DB row + pgvector가 하나의 트랜잭션/작업 체인으로 파기된다. 상태 머신·orphan 검증·감사 로그 정책은 [operations/data-privacy-and-rights.md](../operations/data-privacy-and-rights.md) 참고.
