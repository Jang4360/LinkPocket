package com.linkpocket.contract.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약: plan/01-auth-google-oauth.md "tenant 경계" AC + ADR-006 결정 4(서버가 세션에서 userId 강제)
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - GET /api/me 구현이 클라이언트가 보낸 어떤 값(쿼리 파라미터 userId, 헤더 X-User-Id 등)도
 *    신뢰하지 않고, 오직 인증된 세션(OAuth2User의 sub → app_user 조회)에서만 신원을 결정한다.
 *
 * 이 테스트는 실패 조건 "클라이언트가 보낸 userId 파라미터로 다른 사용자 데이터를 조회할 수 있으면 실패"를
 * 직접 falsify한다: 스푸핑된 값을 보내도 응답은 항상 인증된 본인 것이어야 한다.
 */
class TenantBoundaryContractTest extends AbstractAuthContractTest {

    private String seedUser(String email, String name) {
        String sub = "tenant-sub-" + UUID.randomUUID();
        jdbcTemplate.update(
                "insert into app_user (id, google_sub, email, name, created_at) values (gen_random_uuid(), ?, ?, ?, now())",
                sub, email, name);
        return sub;
    }

    @Test
    void spoofed_query_param_userId_is_ignored() throws Exception {
        String subA = seedUser("tenant-a@example.com", "Tenant A");
        String spoofedOtherUserId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/me")
                        .param("userId", spoofedOtherUserId)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", subA);
                                    a.put("email", "tenant-a@example.com");
                                    a.put("name", "Tenant A");
                                })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("tenant-a@example.com"))
                .andExpect(jsonPath("$.userId").value(org.hamcrest.Matchers.not(spoofedOtherUserId)));
    }

    @Test
    void spoofed_header_x_user_id_is_ignored() throws Exception {
        String subA = seedUser("tenant-b@example.com", "Tenant B");
        String spoofedOtherUserId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/me")
                        .header("X-User-Id", spoofedOtherUserId)
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", subA);
                                    a.put("email", "tenant-b@example.com");
                                    a.put("name", "Tenant B");
                                })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("tenant-b@example.com"))
                .andExpect(jsonPath("$.userId").value(org.hamcrest.Matchers.not(spoofedOtherUserId)));
    }
}
