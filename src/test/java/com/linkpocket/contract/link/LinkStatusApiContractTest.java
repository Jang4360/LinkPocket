package com.linkpocket.contract.link;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약: plan/04-async-ai-pipeline.md AC "클라이언트는 상태 polling API로 QUEUED/PROCESSING/
 * READY/READY_WITHOUT_CONTENT/FAILED 중 하나만 받는다" + api-error-contract.md IDOR 원칙
 * (남의 리소스는 403이 아니라 404로 통일) + invariants.md tenant 격리.
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - GET /api/links/{linkId}/status — 세션 userId 소유의 Link만 조회, 내부 상태(SUMMARIZING 등)를
 *    아래 매핑표대로 외부 상태로 변환해 응답한다. 소유자가 아니면 LINK_NOT_FOUND(404).
 *
 * 내부→외부 상태 매핑: PENDING/FETCHING→QUEUED, SUMMARIZING/CHUNKING/EMBEDDING→PROCESSING,
 * INDEXED→READY, READY_WITHOUT_CONTENT/READY_WITHOUT_INDEX→READY_WITHOUT_CONTENT, FAILED→FAILED.
 */
class LinkStatusApiContractTest extends AbstractLinkContractTest {

    private UUID seedLinkForUser(String userSub, String internalStatus) {
        UUID userId = (UUID) jdbcTemplate.queryForMap(
                "select id from app_user where google_sub = ?", userSub).get("id");
        UUID linkId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into link (id, user_id, url, canonical_url, status, created_at) " +
                        "values (?, ?, ?, ?, ?, now())",
                linkId, userId, "https://example.com/" + linkId, "https://example.com/" + linkId, internalStatus);
        return linkId;
    }

    @Test
    void internal_processing_states_are_exposed_as_processing() throws Exception {
        String sub = seedUser("status-proc@example.com", "Status Proc");
        UUID linkId = seedLinkForUser(sub, "SUMMARIZING");

        mockMvc.perform(get("/api/links/{linkId}/status", linkId)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", sub);
                                    a.put("email", "status-proc@example.com");
                                    a.put("name", "Status Proc");
                                })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void indexed_is_exposed_as_ready() throws Exception {
        String sub = seedUser("status-ready@example.com", "Status Ready");
        UUID linkId = seedLinkForUser(sub, "INDEXED");

        mockMvc.perform(get("/api/links/{linkId}/status", linkId)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", sub);
                                    a.put("email", "status-ready@example.com");
                                    a.put("name", "Status Ready");
                                })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void ai_processing_permanent_failure_is_exposed_as_ready_without_content() throws Exception {
        String sub = seedUser("status-noidx@example.com", "Status NoIdx");
        UUID linkId = seedLinkForUser(sub, "READY_WITHOUT_INDEX");

        mockMvc.perform(get("/api/links/{linkId}/status", linkId)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", sub);
                                    a.put("email", "status-noidx@example.com");
                                    a.put("name", "Status NoIdx");
                                })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_WITHOUT_CONTENT"));
    }

    @Test
    void other_users_link_returns_not_found_not_forbidden() throws Exception {
        String owner = seedUser("status-owner@example.com", "Status Owner");
        UUID linkId = seedLinkForUser(owner, "INDEXED");
        String intruder = seedUser("status-intruder@example.com", "Status Intruder");

        mockMvc.perform(get("/api/links/{linkId}/status", linkId)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", intruder);
                                    a.put("email", "status-intruder@example.com");
                                    a.put("name", "Status Intruder");
                                })))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"));
    }
}
