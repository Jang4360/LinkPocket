package com.linkpocket.contract.category;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약: plan/05-categories.md AC "카테고리를 삭제해도 Link row는 삭제되지 않는다" +
 * "마지막 실제 카테고리 연결이 없어지면 자동으로 카테고리 없음으로 복귀" + ADR-015 결정 1.
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - DELETE /api/categories/{id} — link_category만 CASCADE로 지우고 link row는 그대로.
 *    삭제로 인해 카테고리가 0개가 된 Link는 같은 트랜잭션에서 "카테고리 없음"으로 재연결.
 */
class CategoryDeletionReclassificationContractTest extends AbstractCategoryContractTest {

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

    private void assignCategories(String sub, UUID linkId, String categoryIdsJsonArray) throws Exception {
        mockMvc.perform(authed(put("/api/links/{linkId}/categories", linkId), sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryIds\":" + categoryIdsJsonArray + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleting_category_preserves_link_row_and_reclassifies_to_uncategorized() throws Exception {
        String sub = seedUser("del-reclass@example.com", "Del Reclass");
        UUID uncategorized = systemCategoryId(sub);
        UUID linkId = saveLink(sub, "https://example.com/del-reclass");
        UUID backend = createCategory(sub, "Backend");
        assignCategories(sub, linkId, "[\"" + backend + "\"]");

        mockMvc.perform(authed(delete("/api/categories/{id}", backend), sub))
                .andExpect(status().isNoContent());

        Integer linkStillExists = jdbcTemplate.queryForObject(
                "select count(*) from link where id = ?", Integer.class, linkId);
        assertThat(linkStillExists).isEqualTo(1);
        assertThat(countCategoryLinks(linkId, uncategorized)).isEqualTo(1);
        assertThat(countLinkCategoryRows(linkId)).isEqualTo(1);
    }

    @Test
    void deleting_one_of_multiple_categories_does_not_reclassify() throws Exception {
        String sub = seedUser("del-partial@example.com", "Del Partial");
        UUID linkId = saveLink(sub, "https://example.com/del-partial");
        UUID backend = createCategory(sub, "Backend");
        UUID tools = createCategory(sub, "Tools");
        assignCategories(sub, linkId, "[\"" + backend + "\",\"" + tools + "\"]");

        mockMvc.perform(authed(delete("/api/categories/{id}", backend), sub))
                .andExpect(status().isNoContent());

        assertThat(countCategoryLinks(linkId, tools)).isEqualTo(1);
        assertThat(countLinkCategoryRows(linkId)).isEqualTo(1);
    }

    @Test
    void deleting_category_used_by_multiple_links_reclassifies_each_independently() throws Exception {
        String sub = seedUser("del-multi-link@example.com", "Del MultiLink");
        UUID uncategorized = systemCategoryId(sub);
        UUID onlyLink = saveLink(sub, "https://example.com/only-this-cat");
        UUID multiLink = saveLink(sub, "https://example.com/has-two-cats");
        UUID backend = createCategory(sub, "Backend");
        UUID tools = createCategory(sub, "Tools");
        assignCategories(sub, onlyLink, "[\"" + backend + "\"]");
        assignCategories(sub, multiLink, "[\"" + backend + "\",\"" + tools + "\"]");

        mockMvc.perform(authed(delete("/api/categories/{id}", backend), sub))
                .andExpect(status().isNoContent());

        assertThat(countCategoryLinks(onlyLink, uncategorized)).isEqualTo(1);
        assertThat(countCategoryLinks(multiLink, tools)).isEqualTo(1);
        assertThat(countCategoryLinks(multiLink, uncategorized)).isEqualTo(0);
    }
}
