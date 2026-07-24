package com.linkpocket.contract.link;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약: plan/02-link-save-minimal.md Acceptance Criteria + ADR-010(멱등 저장·canonical URL 정규화)
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - Flyway 마이그레이션 link(id uuid pk, user_id uuid references app_user(id), url text,
 *    canonical_url text, status text, created_at timestamptz) + unique(user_id, canonical_url)
 *  - POST /api/links, 요청 {url}, 인증 필요(세션의 userId 사용, 클라이언트 파라미터 무시)
 *    - 200 {linkId, canonicalUrl, status="PENDING"}
 *    - scheme이 http/https가 아니면 400 LINK_INVALID_URL
 *    - 인증 없으면 401 AUTH_SESSION_INVALID(AUTH 도메인, 이 plan에서 재정의 안 함)
 *  - canonical URL 정규화: scheme은 유지하되 비교 키에서 trailing slash·`www.` 유무를 통일
 *    (ADR-010 결정 2 — 최소 규칙만, tracking parameter는 제거하지 않음)
 */
class LinkSaveContractTest extends AbstractLinkContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ResultActions performSave(String url, String sub) throws Exception {
        return mockMvc.perform(post("/api/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveUrlRequestBody(url))
                .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                        .attributes(a -> {
                            a.put("sub", sub);
                            a.put("email", sub + "@example.com");
                            a.put("name", "Link User");
                        })));
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void saving_valid_url_returns_link_id_and_pending_status() throws Exception {
        String sub = seedUser("save-user@example.com", "Save User");

        performSave("https://example.com/articles/one", sub)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkId").isNotEmpty())
                .andExpect(jsonPath("$.canonicalUrl").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void invalid_scheme_is_rejected_and_not_saved() throws Exception {
        String sub = seedUser("scheme-user@example.com", "Scheme User");

        performSave("javascript:alert(1)", sub)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LINK_INVALID_URL"))
                .andExpect(jsonPath("$.domain").value("LINK"));
    }

    @Test
    void unauthenticated_save_is_rejected() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveUrlRequestBody("https://example.com/no-auth")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_INVALID"));
    }

    @Test
    void trailing_slash_and_www_are_treated_as_the_same_url() throws Exception {
        String sub = seedUser("normalize-user@example.com", "Normalize User");

        MvcResult first = performSave("https://www.example.com/same-article/", sub)
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = performSave("https://example.com/same-article", sub)
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstBody = readJson(first);
        JsonNode secondBody = readJson(second);

        assertThat(secondBody.get("linkId").asText())
                .as("trailing slash·www 유무만 다른 같은 URL은 같은 linkId를 반환해야 한다")
                .isEqualTo(firstBody.get("linkId").asText());
        assertThat(secondBody.get("canonicalUrl").asText())
                .isEqualTo(firstBody.get("canonicalUrl").asText());
    }

    @Test
    void different_users_saving_same_url_get_independent_rows() throws Exception {
        String subA = seedUser("tenant-link-a@example.com", "Tenant Link A");
        String subB = seedUser("tenant-link-b@example.com", "Tenant Link B");
        String sharedUrl = "https://example.com/shared-article";

        MvcResult resultA = performSave(sharedUrl, subA).andExpect(status().isOk()).andReturn();
        MvcResult resultB = performSave(sharedUrl, subB).andExpect(status().isOk()).andReturn();

        String linkIdA = readJson(resultA).get("linkId").asText();
        String linkIdB = readJson(resultB).get("linkId").asText();

        assertThat(linkIdA)
                .as("서로 다른 사용자의 같은 URL 저장은 독립된 row여야 한다(tenant 경계)")
                .isNotEqualTo(linkIdB);
    }
}
