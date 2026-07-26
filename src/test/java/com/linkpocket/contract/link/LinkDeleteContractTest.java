package com.linkpocket.contract.link;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약: plan/02-link-save-minimal.md "(신규)" AC + ADR-010 결정 3(저장 취소 — 2026-07-26)
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - DELETE /api/links/{linkId}
 *    - 본인 소유면 하드 삭제 후 204 (soft-delete 아님 — 재저장이 완전히 새 저장이어야 함)
 *    - 존재하지 않거나 다른 사용자 소유면 404 LINK_NOT_FOUND
 *      (403과 구분하지 않는다 — architecture/api-error-contract.md IDOR 원칙)
 *    - 인증 없으면 401 AUTH_SESSION_INVALID
 */
class LinkDeleteContractTest extends AbstractLinkContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MvcResult saveAs(String url, String sub, String email) throws Exception {
        return mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveUrlRequestBody(url))
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", sub);
                                    a.put("email", email);
                                    a.put("name", "Delete User");
                                })))
                .andExpect(status().isOk())
                .andReturn();
    }

    private ResultActions deleteAs(String linkId, String sub, String email) throws Exception {
        return mockMvc.perform(delete("/api/links/{id}", linkId)
                .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                        .attributes(a -> {
                            a.put("sub", sub);
                            a.put("email", email);
                            a.put("name", "Delete User");
                        })));
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void deleting_own_link_returns_204_and_removes_row() throws Exception {
        String sub = seedUser("delete-owner@example.com", "Delete Owner");
        MvcResult saved = saveAs("https://example.com/to-delete", sub, "delete-owner@example.com");
        String linkId = readJson(saved).get("linkId").asText();

        deleteAs(linkId, sub, "delete-owner@example.com").andExpect(status().isNoContent());

        Integer rowCount = jdbcTemplate.queryForObject(
                "select count(*) from link where id = ?::uuid", Integer.class, linkId);
        assertThat(rowCount).isZero();
    }

    @Test
    void resaving_after_delete_is_treated_as_brand_new_save() throws Exception {
        String sub = seedUser("resave-user@example.com", "Resave User");
        String url = "https://example.com/delete-then-resave";

        MvcResult firstSave = saveAs(url, sub, "resave-user@example.com");
        String firstLinkId = readJson(firstSave).get("linkId").asText();
        deleteAs(firstLinkId, sub, "resave-user@example.com").andExpect(status().isNoContent());

        MvcResult secondSave = mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveUrlRequestBody(url))
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", sub);
                                    a.put("email", "resave-user@example.com");
                                    a.put("name", "Resave User");
                                })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyExisted").value(false))
                .andReturn();

        assertThat(readJson(secondSave).get("linkId").asText())
                .as("삭제 후 재저장은 완전히 새 저장이어야 한다(하드 삭제)")
                .isNotEqualTo(firstLinkId);
    }

    @Test
    void deleting_another_users_link_returns_404_not_found() throws Exception {
        String ownerSub = seedUser("owner@example.com", "Owner");
        String otherSub = seedUser("other@example.com", "Other");
        MvcResult saved = saveAs("https://example.com/owned-by-owner", ownerSub, "owner@example.com");
        String linkId = readJson(saved).get("linkId").asText();

        deleteAs(linkId, otherSub, "other@example.com")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"));

        Integer rowCount = jdbcTemplate.queryForObject(
                "select count(*) from link where id = ?::uuid", Integer.class, linkId);
        assertThat(rowCount)
                .as("남의 링크 삭제 시도는 실패해야 하고, 원 소유자의 row는 그대로 남아야 한다")
                .isEqualTo(1);
    }

    @Test
    void deleting_nonexistent_link_returns_404_not_found() throws Exception {
        String sub = seedUser("delete-ghost@example.com", "Delete Ghost");

        deleteAs(UUID.randomUUID().toString(), sub, "delete-ghost@example.com")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"));
    }
}
