package com.linkpocket.contract.link;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약: plan/05-categories.md AC "사용자가 title/summary를 직접 수정하면 *_source가
 * USER_EDITED로 전환된다" + ADR-015 결정 2.
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - PATCH /api/links/{linkId} {title?, summary?} — 전달된 필드만 갱신하고 그 필드의
 *    *_source를 USER_EDITED로 전환. 소유자가 아니면 LINK_NOT_FOUND(404).
 */
class LinkUserEditContractTest extends AbstractLinkContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private UUID saveLink(String sub, String url) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveUrlRequestBody(url))
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", sub);
                                    a.put("email", sub + "@example.com");
                                    a.put("name", "Edit User");
                                })))
                .andReturn();
        return UUID.fromString(json(result).get("linkId").asText());
    }

    @Test
    void editing_title_marks_source_as_user_edited() throws Exception {
        String sub = seedUser("edit-title@example.com", "Edit Title");
        UUID linkId = saveLink(sub, "https://example.com/edit-title");

        mockMvc.perform(patch("/api/links/{linkId}", linkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"내가 고친 제목\"}")
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", sub);
                                    a.put("email", "edit-title@example.com");
                                    a.put("name", "Edit Title");
                                })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("내가 고친 제목"));

        Map<String, Object> link = jdbcTemplate.queryForMap("select * from link where id = ?", linkId);
        assertThat(link.get("title_source")).isEqualTo("USER_EDITED");
    }

    @Test
    void editing_other_users_link_returns_not_found() throws Exception {
        String owner = seedUser("edit-owner@example.com", "Edit Owner");
        UUID linkId = saveLink(owner, "https://example.com/edit-owner-link");
        String intruder = seedUser("edit-intruder@example.com", "Edit Intruder");

        mockMvc.perform(patch("/api/links/{linkId}", linkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"가로채기\"}")
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", intruder);
                                    a.put("email", "edit-intruder@example.com");
                                    a.put("name", "Edit Intruder");
                                })))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"));
    }
}
