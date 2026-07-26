package com.linkpocket.contract.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약: ADR-006 결정 3 재검토(2026-07-27 확정) — rotation/reuse-detection은 그대로 두고,
 * device_session 생성 후 14일이 지나면 재사용이 아니어도 무조건 재인증을 요구한다("고정 만료").
 *
 * 이 결정은 rotation을 대체하는 게 아니라 그 위에 시간 상한을 얹는 것이다 — 14일 안에서는
 * TokenRotationContractTest의 계약(정상 회전·reuse 탐지)이 그대로 유효해야 한다.
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - POST /api/extension/token/refresh 처리 시, reuse/invalid 판정보다 먼저(또는 함께)
 *    device_session.created_at으로부터 14일이 지났는지 확인한다.
 *  - 14일이 지났으면 재사용 여부와 무관하게 401 AUTH_REFRESH_TOKEN_EXPIRED를 반환하고,
 *    그 device_session(family) 전체를 폐기한다(이후 그 family의 다른 토큰도 재인증 없이는
 *    쓸 수 없어야 한다 — reuse 폐기와 동일한 효과, 다만 사유 코드만 다르다).
 *  - `AuthErrorCode.AUTH_REFRESH_TOKEN_EXPIRED`(401) 신설.
 *  - 14일 이내의 device_session은 이 계약과 무관하게 기존 TokenRotationContractTest 그대로
 *    통과해야 한다(회귀 없음).
 */
class TokenFixedExpiryContractTest extends AbstractAuthContractTest {

    private String requestRefresh(String refreshToken) throws Exception {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }

    private ResultActions performRefresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/extension/token/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestRefresh(refreshToken)));
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString());
    }

    private void backdateDeviceSessionCreatedAt(String email, int daysAgo) {
        jdbcTemplate.update(
                """
                        update device_session set created_at = now() - (? || ' days')::interval
                        where user_id = (select id from app_user where email = ?)
                        """,
                daysAgo, email);
    }

    @Test
    void refresh_after_fourteen_days_is_rejected_even_without_reuse() throws Exception {
        JsonNode initial = exchangeNewDeviceSession("old-session@example.com", "Old Session User");
        String refreshToken = initial.get("refreshToken").asText();
        backdateDeviceSessionCreatedAt("old-session@example.com", 15);

        performRefresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_EXPIRED"));
    }

    @Test
    void expiry_revokes_the_whole_family_like_reuse_does() throws Exception {
        JsonNode initial = exchangeNewDeviceSession("old-family@example.com", "Old Family User");
        String refresh1 = initial.get("refreshToken").asText();

        // 아직 14일 안 됐을 때 정상 회전 한 번 — refresh2를 아직 안 쓴 채로 남겨둔다.
        MvcResult rotated = performRefresh(refresh1).andExpect(status().isOk()).andReturn();
        String refresh2 = readJson(rotated).get("refreshToken").asText();

        backdateDeviceSessionCreatedAt("old-family@example.com", 15);

        // 만료된 family에서, 아직 한 번도 안 쓴 refresh2로도 더 이상 갱신할 수 없어야 한다.
        performRefresh(refresh2)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_EXPIRED"));
    }

    @Test
    void refresh_within_fourteen_days_still_works_normally() throws Exception {
        JsonNode initial = exchangeNewDeviceSession("fresh-session@example.com", "Fresh Session User");
        String refreshToken = initial.get("refreshToken").asText();
        backdateDeviceSessionCreatedAt("fresh-session@example.com", 13);

        performRefresh(refreshToken).andExpect(status().isOk());
    }
}
