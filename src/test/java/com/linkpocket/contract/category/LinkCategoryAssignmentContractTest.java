package com.linkpocket.contract.category;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약: plan/05-categories.md AC "새 Link는 저장 시 기본으로 카테고리 없음에 연결",
 * "실제 카테고리 지정 시 카테고리 없음 자동 해제", "다중 분류", "상호 배타 불변식" + ADR-015 결정 1.
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - LinkService.save가 저장 시 "카테고리 없음"에 기본 연결(같은 트랜잭션).
 *  - PUT /api/links/{linkId}/categories — 실제 카테고리 지정 시 "카테고리 없음" 해제,
 *    빈 배열이면 "카테고리 없음"으로 복귀. 항상 상호 배타 유지.
 */
class LinkCategoryAssignmentContractTest extends AbstractCategoryContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private UUID createCategory(String sub, String name) throws Exception {
        MvcResult result = mockMvc.perform(authed(post("/api/categories"), sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andReturn();
        return UUID.fromString(json(result).get("id").asText());
    }

    private UUID saveLink(String sub, String url) throws Exception {
        MvcResult result = mockMvc.perform(authed(post("/api/links"), sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + url + "\"}"))
                .andReturn();
        return UUID.fromString(json(result).get("linkId").asText());
    }

    private UUID systemCategoryId(String sub) throws Exception {
        mockMvc.perform(authed(get("/api/categories"), sub)).andReturn();
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select id from category where user_id = ? and is_system = true", userIdOf(sub));
        return (UUID) row.get("id");
    }

    @Test
    void new_link_is_uncategorized_by_default() throws Exception {
        String sub = seedUser("assign-default@example.com", "Assign Default");
        UUID uncategorized = systemCategoryId(sub);
        UUID linkId = saveLink(sub, "https://example.com/default-cat");

        assertThat(countCategoryLinks(linkId, uncategorized)).isEqualTo(1);
        assertThat(countLinkCategoryRows(linkId)).isEqualTo(1);
    }

    @Test
    void assigning_real_category_removes_uncategorized() throws Exception {
        String sub = seedUser("assign-real@example.com", "Assign Real");
        UUID uncategorized = systemCategoryId(sub);
        UUID linkId = saveLink(sub, "https://example.com/real-cat");
        UUID backend = createCategory(sub, "Backend");

        mockMvc.perform(authed(put("/api/links/{linkId}/categories", linkId), sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryIds\":[\"" + backend + "\"]}"))
                .andExpect(status().isOk());

        assertThat(countCategoryLinks(linkId, uncategorized)).isEqualTo(0);
        assertThat(countCategoryLinks(linkId, backend)).isEqualTo(1);
        assertThat(countLinkCategoryRows(linkId)).isEqualTo(1);
    }

    @Test
    void link_can_belong_to_multiple_real_categories() throws Exception {
        String sub = seedUser("assign-multi@example.com", "Assign Multi");
        UUID linkId = saveLink(sub, "https://example.com/multi-cat");
        UUID backend = createCategory(sub, "Backend");
        UUID tools = createCategory(sub, "Tools");

        mockMvc.perform(authed(put("/api/links/{linkId}/categories", linkId), sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryIds\":[\"" + backend + "\",\"" + tools + "\"]}"))
                .andExpect(status().isOk());

        assertThat(countCategoryLinks(linkId, backend)).isEqualTo(1);
        assertThat(countCategoryLinks(linkId, tools)).isEqualTo(1);
        assertThat(countLinkCategoryRows(linkId)).isEqualTo(2);
    }

    @Test
    void removing_last_real_category_returns_link_to_uncategorized() throws Exception {
        String sub = seedUser("assign-return@example.com", "Assign Return");
        UUID uncategorized = systemCategoryId(sub);
        UUID linkId = saveLink(sub, "https://example.com/return-cat");
        UUID backend = createCategory(sub, "Backend");

        mockMvc.perform(authed(put("/api/links/{linkId}/categories", linkId), sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryIds\":[\"" + backend + "\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(authed(put("/api/links/{linkId}/categories", linkId), sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryIds\":[]}"))
                .andExpect(status().isOk());

        assertThat(countCategoryLinks(linkId, uncategorized)).isEqualTo(1);
        assertThat(countCategoryLinks(linkId, backend)).isEqualTo(0);
        assertThat(countLinkCategoryRows(linkId)).isEqualTo(1);
    }

    @Test
    void assigning_categories_to_other_users_link_is_not_found() throws Exception {
        String owner = seedUser("assign-owner@example.com", "Assign Owner");
        UUID linkId = saveLink(owner, "https://example.com/owner-link");
        String intruder = seedUser("assign-intruder@example.com", "Assign Intruder");
        UUID intruderCategory = createCategory(intruder, "Intruder Cat");

        mockMvc.perform(authed(put("/api/links/{linkId}/categories", linkId), intruder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryIds\":[\"" + intruderCategory + "\"]}"))
                .andExpect(status().isNotFound());
    }
}
