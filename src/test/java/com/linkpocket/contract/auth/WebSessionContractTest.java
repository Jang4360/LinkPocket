package com.linkpocket.contract.auth;

import org.junit.jupiter.api.Test;
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

    /**
     * 주의(Spring Session + oauth2Login() 테스트 postprocessor의 알려진 한계): oauth2Login()은
     * 필터 체인이 돌기 전에 요청의 세션에 직접 SecurityContext를 심는 방식으로 동작한다. 세션
     * 저장소가 인메모리일 때는 문제없지만, Spring Session이 세션 객체 자체를 필터에서 감싸는
     * 방식으로 바뀌면 oauth2Login()이 심은 속성이 Spring Session이 실제로 추적·저장하는 세션과
     * 다른 객체에 남아 DB(spring_session_attributes)에 영속되지 않는다(직접 확인함 — 로그인 후
     * 그 테이블에 row가 0개였다). 그래서 "쿠키만으로 재요청" 방식으로는 세션 영속 여부 자체를
     * 검증할 수 없다 — 인증이 안 걸린 것처럼 보이는 게 세션이 무효화돼서인지 애초에 이 테스트
     * 기법의 한계인지 구분이 안 된다. 따라서 로그아웃의 핵심 불변조건(DB에 영속된 세션 row가
     * 실제로 사라진다)은 DB를 직접 조회해 검증하고, 로그아웃 자체는 oauth2Login()을 다시 걸어
     * "인증된 사용자가 로그아웃을 호출한다"는 시나리오를 재현한다.
     */
    @Test
    void logout_removes_the_persisted_session_row_from_db() throws Exception {
        String sub = seedUser("logout-user@example.com", "Logout User");
        int sessionsBeforeLogin = countPersistedSessions();

        MvcResult loginResult = mockMvc.perform(get("/api/me")
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", sub);
                                    a.put("email", "logout-user@example.com");
                                    a.put("name", "Logout User");
                                })))
                .andExpect(status().isOk())
                .andReturn();

        org.assertj.core.api.Assertions.assertThat(countPersistedSessions())
                .as("로그인 후 세션이 DB에 실제로 영속돼야 한다")
                .isEqualTo(sessionsBeforeLogin + 1);

        // oauth2Login()은 "인증된 사용자가 호출한다"는 조건을 재현하기 위해 다시 걸고, 로그인 때
        // 받은 쿠키(세션 ID)도 함께 보내 로그아웃이 "바로 그 세션"을 무효화하는지 확인한다.
        mockMvc.perform(post("/api/logout")
                        .cookie(loginResult.getResponse().getCookies())
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login()
                                .attributes(a -> {
                                    a.put("sub", sub);
                                    a.put("email", "logout-user@example.com");
                                    a.put("name", "Logout User");
                                })))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(countPersistedSessions())
                .as("로그아웃 후 DB에 영속된 세션 row는 실제로 삭제돼야 한다")
                .isEqualTo(sessionsBeforeLogin);
    }

    @Test
    void unauthenticated_me_without_any_session_is_rejected() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_INVALID"));
    }

    private int countPersistedSessions() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from spring_session", Integer.class);
        return count == null ? 0 : count;
    }
}
