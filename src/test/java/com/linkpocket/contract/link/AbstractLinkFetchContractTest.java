package com.linkpocket.contract.link;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.linkpocket.link.LinkFetchService;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

/**
 * plan-03(safe-fetch-extract) 계약 테스트들의 공통 기반.
 * 이 plan은 공개 API가 없다 — LinkFetchService.fetchAndExtract(UUID)를 직접 호출해 검증한다
 * (docs/plan/03-safe-fetch-extract.md "스코프 판단").
 *
 * 이 파일도 contract 패키지 소속이며 사람/Claude만 수정한다(docs/development-loop.md).
 *
 * === SSRF 테스트와 WireMock의 충돌, 그리고 이 계약이 요구하는 해법 ===
 * WireMock은 기본적으로 loopback(127.0.0.1)에 바인딩되는데, 이는 우리가 정확히 차단해야 하는
 * 대역이다. 그래서 이 기반 클래스는 "WireMock의 정확한 host만" 예외로 허용하는 test-only 설정을
 * 주입한다 — loopback 전체(127.0.0.0/8)를 허용하는 게 아니라 딱 WireMock 하나만이다.
 *
 * green으로 만들려면 Codex가 지원해야 하는 것:
 *  - `linkpocket.fetch.ssrf.allowed-test-hosts` 프로퍼티(콤마 구분 host 목록, 예: "127.0.0.1")를
 *    읽어 SSRF 검증에서 "정확히 이 host 문자열"만 예외로 허용한다. 이 프로퍼티가 없으면(운영)
 *    아무 예외도 없다 — application.yml에 이 키를 두지 않는다.
 *  - 이 예외는 loopback 전체를 허용하는 게 아니므로, `127.0.0.2`(같은 loopback 대역의 다른
 *    주소)나 `169.254.169.254`(메타데이터) 등은 이 설정과 무관하게 여전히 차단돼야 한다
 *    (AbstractLinkFetchContractTest는 WireMock 주소만 예외 목록에 넣는다).
 */
@SpringBootTest
abstract class AbstractLinkFetchContractTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    protected static final WireMockServer ORIGIN = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        POSTGRES.start();
        ORIGIN.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // WireMock의 정확한 host만 SSRF 예외 목록에 넣는다 — loopback 전체를 여는 게 아니다.
        registry.add("linkpocket.fetch.ssrf.allowed-test-hosts", () -> "127.0.0.1");
        // timeout 계약 테스트가 실제로 몇 초씩 기다리지 않도록 예산을 작게 오버라이드한다.
        // 운영 기본값(3000/5000/15000ms, ADR-011)과 같은 키를 그대로 쓰되 값만 다르게 둔다.
        registry.add("linkpocket.fetch.timeout.connect-ms", () -> "300");
        registry.add("linkpocket.fetch.timeout.response-ms", () -> "500");
        registry.add("linkpocket.fetch.timeout.read-ms", () -> "1000");
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected LinkFetchService linkFetchService;

    @AfterEach
    void resetOriginStubs() {
        ORIGIN.resetAll();
    }

    protected String originUrl(String path) {
        return ORIGIN.baseUrl() + path;
    }

    /** app_user + PENDING 상태의 link를 직접 seed하고 linkId를 반환한다. */
    protected UUID seedPendingLink(String url) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into app_user (id, google_sub, email, name, created_at) values (?, ?, ?, ?, now())",
                userId, "fetch-sub-" + userId, "fetch-user-" + userId + "@example.com", "Fetch User");

        UUID linkId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into link (id, user_id, url, canonical_url, status, created_at) values (?, ?, ?, ?, 'PENDING', now())",
                linkId, userId, url, url);
        return linkId;
    }

    protected Map<String, Object> loadLink(UUID linkId) {
        return jdbcTemplate.queryForMap("select * from link where id = ?", linkId);
    }
}
