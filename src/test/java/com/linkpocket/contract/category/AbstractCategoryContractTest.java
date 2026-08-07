package com.linkpocket.contract.category;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

/**
 * plan-05(categories) 계약 테스트들의 공통 기반.
 * 이 파일도 contract 패키지 소속이며 사람/Claude만 수정한다(docs/development-loop.md).
 *
 * 싱글턴 컨테이너 패턴(의도적으로 @Container를 안 씀)은 AbstractLinkContractTest와 동일한 이유다
 * — 여러 서브클래스가 공유하는데 @Container로 관리하면 먼저 끝나는 서브클래스가 컨테이너를
 * 정지시켜 이후 서브클래스가 죽은 컨테이너에 연결을 시도한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractCategoryContractTest {

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

    /** app_user를 직접 seed하고 Google sub를 반환한다 — link 계약 테스트와 동일한 패턴. */
    protected String seedUser(String email, String name) {
        String sub = "category-sub-" + UUID.randomUUID();
        jdbcTemplate.update(
                "insert into app_user (id, google_sub, email, name, created_at) values (gen_random_uuid(), ?, ?, ?, now())",
                sub, email, name);
        return sub;
    }

    protected UUID userIdOf(String sub) {
        return (UUID) jdbcTemplate.queryForMap("select id from app_user where google_sub = ?", sub).get("id");
    }

    /** app_user + PENDING 상태 Link를 직접 seed하고 linkId를 반환한다. */
    protected UUID seedLink(String sub) {
        UUID userId = userIdOf(sub);
        UUID linkId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into link (id, user_id, url, canonical_url, status, created_at) values (?, ?, ?, ?, 'PENDING', now())",
                linkId, userId, "https://example.com/" + linkId, "https://example.com/" + linkId);
        return linkId;
    }

    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String sub) {
        return builder.with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                .attributes(a -> {
                    a.put("sub", sub);
                    a.put("email", sub + "@example.com");
                    a.put("name", "Category User");
                }));
    }

    protected Integer countCategoryLinks(UUID linkId, UUID categoryId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from link_category where link_id = ? and category_id = ?",
                Integer.class, linkId, categoryId);
    }

    protected Integer countLinkCategoryRows(UUID linkId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from link_category where link_id = ?", Integer.class, linkId);
    }
}
