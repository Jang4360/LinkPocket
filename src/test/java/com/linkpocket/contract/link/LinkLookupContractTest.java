package com.linkpocket.contract.link;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약: plan/02-link-save-minimal.md "(신규)" AC + ADR-010 결정 3(저장 취소·조회 — 2026-07-26)
 *
 * 익스텐션 토글 아이콘이 팝업을 열 때마다 이 엔드포인트로 현재 탭 URL의 저장 여부를 물어봐서
 * 로컬 상태 없이 서버 진실로 아이콘 상태를 복원한다.
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - GET /api/links/lookup?url=... — 세션 userId + 정규화된 canonical_url로 존재 여부만 조회
 *    (부작용 없음, row를 만들지 않는다)
 *    - 저장돼 있으면 200 {saved: true, linkId}
 *    - 저장 안 돼 있으면 200 {saved: false, linkId: null} (404 아님 — "안 저장됨"은 정상 상태)
 *    - 인증 없으면 401 AUTH_SESSION_INVALID
 */
class LinkLookupContractTest extends AbstractLinkContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MvcResult performLookup(String url, String sub) throws Exception {
        return mockMvc.perform(get("/api/links/lookup")
                        .param("url", url)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", sub);
                                    a.put("email", sub + "@example.com");
                                    a.put("name", "Lookup User");
                                })))
                .andReturn();
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void lookup_returns_saved_false_when_never_saved() throws Exception {
        String sub = seedUser("lookup-none@example.com", "Lookup None");

        mockMvc.perform(get("/api/links/lookup")
                        .param("url", "https://example.com/never-saved")
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", sub);
                                    a.put("email", "lookup-none@example.com");
                                    a.put("name", "Lookup None");
                                })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.linkId").doesNotExist());
    }

    @Test
    void lookup_returns_saved_true_with_link_id_when_previously_saved() throws Exception {
        String sub = seedUser("lookup-yes@example.com", "Lookup Yes");
        String url = "https://example.com/already-saved";

        MvcResult saveResult = mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveUrlRequestBody(url))
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", sub);
                                    a.put("email", "lookup-yes@example.com");
                                    a.put("name", "Lookup Yes");
                                })))
                .andExpect(status().isOk())
                .andReturn();
        String savedLinkId = readJson(saveResult).get("linkId").asText();

        MvcResult lookupResult = performLookup(url, sub);
        readJson(lookupResult);

        org.assertj.core.api.Assertions.assertThat(readJson(lookupResult).get("saved").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(readJson(lookupResult).get("linkId").asText())
                .isEqualTo(savedLinkId);
    }
}
