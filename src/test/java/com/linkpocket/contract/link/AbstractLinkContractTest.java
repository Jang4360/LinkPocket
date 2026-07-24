package com.linkpocket.contract.link;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

/**
 * plan-02(link-save-minimal) 계약 테스트들의 공통 기반.
 * 이 파일도 contract 패키지 소속이며 사람/Claude만 수정한다(docs/development-loop.md).
 *
 * 주의(싱글턴 컨테이너 패턴 — 의도적으로 @Container/@Testcontainers를 쓰지 않음): 이 필드는
 * 여러 서브클래스가 상속해 공유한다. @Container로 관리하면 JUnit5가 "먼저 끝나는 서브클래스"의
 * afterAll에서 컨테이너를 정지시켜, 전체 스위트 실행 시 이후 서브클래스가 이미 죽은 컨테이너에
 * 연결을 시도해 CannotGetJdbcConnectionException이 난다(auth 계약 테스트에서 실제로 겪은 문제,
 * AbstractAuthContractTest 참고). static 블록에서 1회 시작하고 정지는 Ryuk에 맡긴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractLinkContractTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** app_user를 직접 seed하고 Google sub를 반환한다 — auth 계약 테스트와 동일한 패턴. */
    protected String seedUser(String email, String name) {
        String sub = "link-sub-" + UUID.randomUUID();
        jdbcTemplate.update(
                "insert into app_user (id, google_sub, email, name, created_at) values (gen_random_uuid(), ?, ?, ?, now())",
                sub, email, name);
        return sub;
    }

    protected Integer countLinkRows(String canonicalUrl) {
        return jdbcTemplate.queryForObject(
                "select count(*) from link where canonical_url = ?",
                Integer.class, canonicalUrl);
    }

    protected String saveUrlRequestBody(String url) {
        return "{\"url\":\"" + url + "\"}";
    }
}
