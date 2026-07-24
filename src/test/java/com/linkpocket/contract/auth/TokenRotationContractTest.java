package com.linkpocket.contract.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약: plan/01-auth-google-oauth.md "익스텐션 로그인" AC + ADR-006 결정 3(rotation·reuse detection — 필수 완화책)
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - POST /api/extension/token/refresh, 요청: {refreshToken}
 *    - 유효한 refresh token → 200 {accessToken, refreshToken(새 값), expiresIn}
 *      이전 refresh token은 이 시점에 즉시 폐기(rotation)
 *    - 이미 폐기된(rotation으로 소비된) refresh token 재사용 → 401 AUTH_REFRESH_TOKEN_REUSED
 *      + 그 token이 속한 family(=device session) 전체를 폐기
 *    - family가 폐기된 뒤에는, 그 family에서 나온 다른(아직 안 썼던) refresh token으로도
 *      refresh를 시도하면 401 AUTH_REFRESH_TOKEN_INVALID (reuse가 아니라 invalid — family가 죽었으므로)
 *    - 존재하지 않는/형식이 잘못된 refresh token → 401 AUTH_REFRESH_TOKEN_INVALID
 */
class TokenRotationContractTest extends AbstractAuthContractTest {

    private String requestRefresh(String refreshToken) throws Exception {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }

    @Test
    void refresh_rotates_token_and_old_one_becomes_unusable() throws Exception {
        JsonNode initial = exchangeNewDeviceSession("rotate-user@example.com", "Rotate User");
        String refresh1 = initial.get("refreshToken").asText();

        MvcResult refreshed = performRefresh(refresh1)
                .andExpect(status().isOk())
                .andReturn();
        JsonNode second = readJson(refreshed);
        String refresh2 = second.get("refreshToken").asText();

        assertThat(refresh2).isNotEqualTo(refresh1);
        assertThat(second.get("accessToken").asText()).isNotBlank();
        assertThat(second.get("expiresIn").asInt()).isLessThanOrEqualTo(900);
    }

    @Test
    void reusing_rotated_refresh_token_is_detected_and_rejected() throws Exception {
        JsonNode initial = exchangeNewDeviceSession("reuse-user@example.com", "Reuse User");
        String refresh1 = initial.get("refreshToken").asText();

        // 1회 정상 회전
        performRefresh(refresh1).andExpect(status().isOk());

        // 이미 소비된 refresh1을 다시 사용 → reuse 탐지
        performRefresh(refresh1)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_REUSED"));
    }

    @Test
    void family_revocation_invalidates_even_the_latest_unused_token() throws Exception {
        JsonNode initial = exchangeNewDeviceSession("family-user@example.com", "Family User");
        String refresh1 = initial.get("refreshToken").asText();

        MvcResult firstRotation = performRefresh(refresh1).andExpect(status().isOk()).andReturn();
        String refresh2 = readJson(firstRotation).get("refreshToken").asText();

        // refresh1 재사용 → family 전체 폐기 트리거
        performRefresh(refresh1).andExpect(status().isUnauthorized());

        // family가 죽었으므로, 아직 한 번도 안 쓴 refresh2조차 더 이상 유효하지 않다
        performRefresh(refresh2)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_INVALID"));
    }

    @Test
    void unknown_refresh_token_is_invalid_not_reused() throws Exception {
        performRefresh("this-token-was-never-issued")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_INVALID"));
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
}
