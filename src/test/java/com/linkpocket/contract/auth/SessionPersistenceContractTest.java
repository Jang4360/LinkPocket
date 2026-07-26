package com.linkpocket.contract.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약: ADR-006 결정 1 회고(2026-07-26) — "서버 세션 스토어(DB)"라는 원래 결정이 실제로는
 * Tomcat 기본 인메모리 세션으로 구현돼 있었다(서버 재시작 시 전체 로그아웃되는 격차).
 * 이 테스트는 세션이 실제로 DB에 영속되는지를 직접 검증한다.
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - Spring Session JDBC 도입(spring-session-jdbc 의존성, spring.session.store-type=jdbc)
 *  - Flyway로 Spring Session 표준 스키마(SPRING_SESSION, SPRING_SESSION_ATTRIBUTES 테이블) 적용
 *  - 로그인 후 SPRING_SESSION 테이블에 실제로 row가 생겨야 한다(인메모리가 아니라 DB에
 *    영속된다는 뜻) — 이 테스트는 그 테이블에 직접 쿼리해 확인한다.
 */
class SessionPersistenceContractTest extends AbstractAuthContractTest {

    private String seedUser(String email, String name) {
        String sub = "persist-sub-" + UUID.randomUUID();
        jdbcTemplate.update(
                "insert into app_user (id, google_sub, email, name, created_at) values (gen_random_uuid(), ?, ?, ?, now())",
                sub, email, name);
        return sub;
    }

    @Test
    void login_persists_a_row_in_the_db_backed_session_store() throws Exception {
        String sub = seedUser("persist-user@example.com", "Persist User");

        mockMvc.perform(get("/api/me")
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", sub);
                                    a.put("email", "persist-user@example.com");
                                    a.put("name", "Persist User");
                                })))
                .andExpect(status().isOk());

        Integer sessionRowCount = jdbcTemplate.queryForObject(
                "select count(*) from spring_session", Integer.class);
        assertThat(sessionRowCount)
                .as("로그인 후 세션이 DB(spring_session 테이블)에 실제로 영속돼야 한다 — "
                        + "인메모리 세션이면 이 테이블 자체가 없어 쿼리가 실패한다")
                .isGreaterThan(0);
    }
}
