package com.linkpocket.contract.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약: plan/01-auth-google-oauth.md "웹 로그인" AC + ADR-006 결정 1(HttpOnly 쿠키+서버 세션 스토어)
 *
 * 이 테스트는 Google과의 실제 OAuth 핸드셰이크(인가 코드 교환)는 검증하지 않는다 — 그건
 * Spring Security의 OAuth2 Login이 이미 보장하는 프로토콜 동작이라 재검증하지 않고,
 * spring-security-test의 oauth2Login()으로 "로그인된 이후" 상태를 직접 구성해
 * 우리가 만든 계약(세션 쿠키 플래그, /api/me, /api/logout)만 검증한다.
 * 신규 사용자 최초 로그인 시 실제 provisioning은 ExtensionPkceContractTest가 검증한다.
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - Flyway 마이그레이션으로 테이블 app_user(id uuid pk, google_sub text unique, email text, name text, created_at timestamptz)
 *  - GET /api/me: 인증된 사용자의 OAuth2User 속성 "sub"로 app_user를 조회해
 *    {userId, email, name} 반환 (userId는 app_user.id, Google sub 아님)
 *  - 세션 쿠키: HttpOnly, Secure, SameSite=Lax 속성 포함 (Set-Cookie 헤더)
 *  - POST /api/logout: 서버 세션 무효화 + 쿠키 만료(Set-Cookie ...Max-Age=0)
 *  - 로그아웃 후 같은 세션으로 GET /api/me → 401 AUTH_SESSION_INVALID
 *
 * 필요 의존성(build.gradle.kts, 아직 없으면 추가): spring-boot-starter-oauth2-client,
 * spring-security-test(testImplementation)
 */
class WebSessionContractTest extends AbstractAuthContractTest {

    private String seedUser(String email, String name) {
        String sub = "google-sub-" + UUID.randomUUID();
        jdbcTemplate.update(
                "insert into app_user (id, google_sub, email, name, created_at) values (gen_random_uuid(), ?, ?, ?, now())",
                sub, email, name);
        return sub;
    }

    @Test
    void authenticated_me_returns_own_profile_from_persisted_user() throws Exception {
        String sub = seedUser("web-user@example.com", "Web User");

        mockMvc.perform(get("/api/me")
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", sub);
                                    a.put("email", "web-user@example.com");
                                    a.put("name", "Web User");
                                })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.email").value("web-user@example.com"))
                .andExpect(jsonPath("$.name").value("Web User"));
    }

    @Test
    void session_cookie_is_httponly_secure_samesite_lax() throws Exception {
        String sub = seedUser("cookie-user@example.com", "Cookie User");

        MvcResult result = mockMvc.perform(get("/api/me")
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", sub);
                                    a.put("email", "cookie-user@example.com");
                                    a.put("name", "Cookie User");
                                })))
                .andExpect(status().isOk())
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
        String sessionCookie = findSessionSetCookieHeader(setCookies);
        assertThat(sessionCookie).as("Set-Cookie 헤더에 세션 쿠키가 있어야 한다").isNotNull();
        assertThat(sessionCookie).containsIgnoringCase("HttpOnly");
        assertThat(sessionCookie).containsIgnoringCase("Secure");
        assertThat(sessionCookie).containsIgnoringCase("SameSite=Lax");
    }

    @Test
    void logout_invalidates_session_and_subsequent_me_returns_401() throws Exception {
        String sub = seedUser("logout-user@example.com", "Logout User");

        // 1) 최초 요청으로 실제 서버 세션에 인증 컨텍스트를 심는다.
        MvcResult loginResult = mockMvc.perform(get("/api/me")
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", sub);
                                    a.put("email", "logout-user@example.com");
                                    a.put("name", "Logout User");
                                })))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        org.assertj.core.api.Assertions.assertThat(session).as("인증 후 서버 세션이 존재해야 한다").isNotNull();

        // 2) 같은 세션으로 로그아웃 — oauth2Login()을 다시 적용하지 않고 세션만 재사용한다
        //    (매 요청마다 oauth2Login()을 새로 걸면 서버 세션 무효화를 검증할 수 없다).
        mockMvc.perform(post("/api/logout").session(session))
                .andExpect(status().isNoContent());

        // 3) 같은(무효화된) 세션으로 재요청하면 인증되지 않은 것으로 취급돼야 한다.
        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_INVALID"));
    }
}
