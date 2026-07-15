package com.linkpocket.contract;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약: plan/00-walking-skeleton.md
 *
 * 이 테스트가 green이 되도록 Codex가 스캐폴딩·구현한다.
 * 계약 테스트(src/test/**/contract/**)는 사람/Claude만 수정한다. (docs/development-loop.md)
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - 단일 Gradle 모듈(Kotlin DSL) + Spring Boot 3.4 + Actuator + Flyway
 *    + spring-boot-starter-test + Testcontainers(postgresql) 의존성
 *  - @SpringBootApplication 클래스 (예: com.linkpocket.LinkpocketApplication)
 *  - application.yml: Flyway 활성, ddl-auto=validate|none, Actuator health 노출
 *  - db/migration/V1__baseline.sql (빈 baseline 또는 최소 테이블)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WalkingSkeletonContractTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    /** AC: GET /actuator/health → 200 {"status":"UP"} */
    @Test
    void actuator_health_returns_UP() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /** 불변식: 통합테스트는 Testcontainers Postgres 위에서 DB에 실제로 도달한다. */
    @Test
    void database_is_reachable_via_testcontainers() {
        Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
        assertThat(one).isEqualTo(1);
    }

    /** 불변식: 스키마는 Flyway가 단일 소스로 적용한다(ddl-auto가 아니라). V1이 적용돼 이력이 남는다. */
    @Test
    void flyway_migration_history_exists() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(1);
    }
}
