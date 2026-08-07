package com.linkpocket.contract.category;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약: plan/05-categories.md AC "카테고리 CRUD" + tenant 격리 + 중복 이름 거부.
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - POST/GET/PATCH/DELETE /api/categories — 세션 userId 소유로만 조작
 *  - 같은 사용자 안에서 이름 중복 시 CATEGORY_DUPLICATE_NAME(409)
 *  - 남의 카테고리 조작 시 CATEGORY_NOT_FOUND(404, IDOR 통일 — api-error-contract.md)
 */
class CategoryCrudContractTest extends AbstractCategoryContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void create_and_list_category() throws Exception {
        String sub = seedUser("crud-a@example.com", "Crud A");

        MvcResult created = mockMvc.perform(authed(post("/api/categories"), sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Backend\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Backend"))
                .andReturn();
        assertThat(json(created).get("id")).isNotNull();

        mockMvc.perform(authed(get("/api/categories"), sub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Backend')]").exists())
                .andExpect(jsonPath("$[?(@.isSystem==true)]").exists());
    }

    @Test
    void listing_categories_first_time_auto_creates_system_uncategorized() throws Exception {
        String sub = seedUser("crud-sys@example.com", "Crud Sys");

        MvcResult result = mockMvc.perform(authed(get("/api/categories"), sub))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode categories = json(result);
        long systemCount = 0;
        for (JsonNode category : categories) {
            if (category.get("isSystem").asBoolean()) {
                systemCount++;
            }
        }
        assertThat(systemCount).isEqualTo(1);
    }

    @Test
    void duplicate_name_within_same_user_is_rejected() throws Exception {
        String sub = seedUser("crud-dup@example.com", "Crud Dup");
        mockMvc.perform(authed(post("/api/categories"), sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tools\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(authed(post("/api/categories"), sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tools\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_DUPLICATE_NAME"));
    }

    @Test
    void rename_and_delete_own_category() throws Exception {
        String sub = seedUser("crud-rn@example.com", "Crud Rename");
        MvcResult created = mockMvc.perform(authed(post("/api/categories"), sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reference\"}"))
                .andReturn();
        String categoryId = json(created).get("id").asText();

        mockMvc.perform(authed(patch("/api/categories/{id}", categoryId), sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reference2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Reference2"));

        mockMvc.perform(authed(delete("/api/categories/{id}", categoryId), sub))
                .andExpect(status().isNoContent());
    }

    @Test
    void other_users_category_operations_return_not_found_not_forbidden() throws Exception {
        String owner = seedUser("crud-owner@example.com", "Crud Owner");
        MvcResult created = mockMvc.perform(authed(post("/api/categories"), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Private\"}"))
                .andReturn();
        String categoryId = json(created).get("id").asText();
        String intruder = seedUser("crud-intruder@example.com", "Crud Intruder");

        mockMvc.perform(authed(patch("/api/categories/{id}", categoryId), intruder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hacked\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        mockMvc.perform(authed(delete("/api/categories/{id}", categoryId), intruder))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }
}
