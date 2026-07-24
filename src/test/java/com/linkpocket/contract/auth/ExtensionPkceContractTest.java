package com.linkpocket.contract.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약: plan/01-auth-google-oauth.md "익스텐션 로그인" AC + ADR-006 결정 2(PKCE)
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - POST /api/extension/oauth/callback
 *    요청: {code, codeVerifier, redirectUri}
 *    처리: 서버가 code+codeVerifier를 Google 토큰 엔드포인트(spring.security.oauth2.client
 *          .provider.google.token-uri 프로퍼티, 테스트에서는 WireMock으로 override)로 교환 →
 *          받은 access_token으로 user-info-uri 호출해 {sub,email,name} 조회 →
 *          app_user upsert(googleSub 기준) → DeviceSession 생성 → 응답
 *    응답 200: {accessToken, refreshToken, expiresIn}  (expiresIn ≤ 900초, ADR-006 완화책)
 *  - Google 토큰 교환이 4xx(예: invalid_grant)로 실패하면 → 400 AUTH_PKCE_VERIFICATION_FAILED
 *  - Google 토큰 교환이 5xx/타임아웃으로 실패하면 → 502 AUTH_OAUTH_CODE_EXCHANGE_FAILED
 *  - 이 엔드포인트로 생성된 사용자는 app_user 테이블에 1행만 존재해야 한다(같은 sub 재로그인 시 upsert)
 *
 * 필요 의존성(build.gradle.kts): org.wiremock:wiremock (testImplementation) 또는 동등 라이브러리.
 */
class ExtensionPkceContractTest extends AbstractAuthContractTest {

    @Test
    void successful_code_exchange_returns_tokens_with_short_access_ttl() throws Exception {
        JsonNode response = exchangeNewDeviceSession("ext-user@example.com", "Ext User");

        assertThat(response.get("accessToken").asText()).isNotBlank();
        assertThat(response.get("refreshToken").asText()).isNotBlank();
        assertThat(response.get("expiresIn").asInt())
                .as("ADR-006: access token TTL은 15분(900초) 이하여야 한다")
                .isLessThanOrEqualTo(900);
    }

    @Test
    void successful_exchange_provisions_exactly_one_user_row() throws Exception {
        exchangeNewDeviceSession("provision-user@example.com", "Provision User");

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from app_user where email = ?",
                Integer.class, "provision-user@example.com");
        assertThat(count).isEqualTo(1);
    }

    @Test
    void google_rejecting_code_verifier_maps_to_pkce_verification_failed() throws Exception {
        stubGoogleTokenExchangeInvalidGrant();

        String requestBody = "{"
                + "\"code\":\"fake-auth-code\","
                + "\"codeVerifier\":\"" + newCodeVerifier() + "\","
                + "\"redirectUri\":\"https://test-extension-id.chromiumapp.org/\""
                + "}";

        mockMvc.perform(post("/api/extension/oauth/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_PKCE_VERIFICATION_FAILED"))
                .andExpect(jsonPath("$.domain").value("AUTH"));
    }

    @Test
    void google_provider_outage_maps_to_code_exchange_failed() throws Exception {
        stubGoogleTokenExchangeServerError();

        String requestBody = "{"
                + "\"code\":\"fake-auth-code\","
                + "\"codeVerifier\":\"" + newCodeVerifier() + "\","
                + "\"redirectUri\":\"https://test-extension-id.chromiumapp.org/\""
                + "}";

        mockMvc.perform(post("/api/extension/oauth/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AUTH_OAUTH_CODE_EXCHANGE_FAILED"));
    }
}
