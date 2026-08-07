package com.linkpocket.contract.category;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약: plan/05-categories.md AC ""카테고리 없음"은 사용자가 삭제·이름변경 할 수 없다" +
 * 불변식 "사용자별 "카테고리 없음" row는 정확히 1개다".
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - system(is_system=true) 카테고리에 대한 PATCH/DELETE 시도는 CATEGORY_SYSTEM_PROTECTED(400).
 */
class CategorySystemProtectionContractTest extends AbstractCategoryContractTest {

    private UUID systemCategoryId(String sub) throws Exception {
        mockMvc.perform(authed(get("/api/categories"), sub)).andReturn();
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select id from category where user_id = ? and is_system = true", userIdOf(sub));
        return (UUID) row.get("id");
    }

    @Test
    void renaming_system_category_is_rejected() throws Exception {
        String sub = seedUser("sys-rename@example.com", "Sys Rename");
        UUID systemId = systemCategoryId(sub);

        mockMvc.perform(authed(patch("/api/categories/{id}", systemId), sub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"바꿔치기\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_SYSTEM_PROTECTED"));
    }

    @Test
    void deleting_system_category_is_rejected() throws Exception {
        String sub = seedUser("sys-delete@example.com", "Sys Delete");
        UUID systemId = systemCategoryId(sub);

        mockMvc.perform(authed(delete("/api/categories/{id}", systemId), sub))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_SYSTEM_PROTECTED"));
    }

    @Test
    void exactly_one_system_category_exists_per_user_even_after_repeated_listing() throws Exception {
        String sub = seedUser("sys-once@example.com", "Sys Once");

        mockMvc.perform(authed(get("/api/categories"), sub)).andReturn();
        mockMvc.perform(authed(get("/api/categories"), sub)).andReturn();
        mockMvc.perform(authed(get("/api/categories"), sub)).andReturn();

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from category where user_id = ? and is_system = true",
                Integer.class, userIdOf(sub));
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }
}
