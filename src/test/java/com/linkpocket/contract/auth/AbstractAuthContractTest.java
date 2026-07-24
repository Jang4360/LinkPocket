package com.linkpocket.contract.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * plan-01(auth-google-oauth) 계약 테스트들의 공통 기반.
 * Testcontainers Postgres + MockMvc + WireMock(Google 대역) + PKCE 헬퍼를 제공한다.
 * 이 파일도 contract 패키지 소속이며 사람/Claude만 수정한다(docs/development-loop.md).
 *
 * 주의(이름 충돌 회피): WireMock의 stub 매칭 메서드 get(...)/post(...)는 여기서
 * "com.github.tomakehurst.wiremock.client.WireMock.post(...)"처럼 완전정규명으로만 쓴다.
 * MockMvcRequestBuilders.post(...)를 정적 임포트해 실제 HTTP 요청 빌더로 쓰기 때문이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractAuthContractTest {

    /**
     * Singleton 컨테이너 패턴(의도적으로 @Container/@Testcontainers를 쓰지 않음) — 이 필드는
     * 추상 클래스에 선언돼 여러 서브클래스(WebSessionContractTest 등)가 상속해 공유한다.
     * @Container로 관리하면 JUnit5가 "먼저 끝나는 서브클래스"의 afterAll에서 컨테이너를 정지시켜,
     * 전체 스위트 실행 시 이후 서브클래스가 이미 죽은 컨테이너에 연결을 시도해
     * CannotGetJdbcConnectionException이 난다(개별 클래스 단독 실행 땐 재현 안 됨).
     * GOOGLE과 동일하게 static 블록에서 1회 시작하고, 정지는 Ryuk(컨테이너 종료 시 자동 정리)에 맡긴다.
     */
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    protected static final WireMockServer GOOGLE = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        POSTGRES.start();
        GOOGLE.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /**
     * Google 토큰/유저정보 엔드포인트를 WireMock으로 대체한다. 웹 로그인(oauth2Login() 우회)에는
     * 쓰이지 않고, 익스텐션 PKCE 코드 교환처럼 서버가 직접 Google에 HTTP 호출하는 경로에만 쓰인다.
     */
    @DynamicPropertySource
    static void googleProviderUris(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.client.provider.google.token-uri", () -> GOOGLE.baseUrl() + "/token");
        registry.add("spring.security.oauth2.client.provider.google.user-info-uri", () -> GOOGLE.baseUrl() + "/userinfo");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void resetGoogleStubs() {
        GOOGLE.resetAll();
    }

    /** RFC 7636 PKCE code_verifier 생성 (43~128자, unreserved 문자). */
    protected static String newCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** RFC 7636 S256: BASE64URL(SHA256(code_verifier)). */
    protected static String codeChallengeS256(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 응답의 모든 Set-Cookie 헤더 중 이름에 "SESSION"을 포함하는 첫 값을 찾는다(구현이 고른 세션 쿠키명 무관). */
    protected static String findSessionSetCookieHeader(List<String> setCookieHeaders) {
        return setCookieHeaders.stream()
                .filter(h -> h.toUpperCase().contains("SESSION"))
                .findFirst()
                .orElse(null);
    }

    protected void stubGoogleTokenExchangeSuccess(String accessTokenValue) {
        GOOGLE.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/token"))
                .willReturn(okJson("{\"access_token\":\"" + accessTokenValue
                        + "\",\"token_type\":\"Bearer\",\"expires_in\":3599}")));
    }

    protected void stubGoogleUserInfo(String sub, String email, String name) {
        GOOGLE.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/userinfo"))
                .willReturn(okJson("{\"sub\":\"" + sub + "\",\"email\":\"" + email + "\",\"name\":\"" + name + "\"}")));
    }

    /** Google이 code/verifier 불일치로 거절하는 상황(invalid_grant, 4xx) — PKCE 검증 실패로 매핑돼야 한다. */
    protected void stubGoogleTokenExchangeInvalidGrant() {
        GOOGLE.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/token"))
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\"}")));
    }

    /** Google 쪽 일시 장애(5xx) — provider 장애로 매핑돼야 한다(PKCE 문제와 구분). */
    protected void stubGoogleTokenExchangeServerError() {
        GOOGLE.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/token"))
                .willReturn(aResponse().withStatus(500)));
    }

    /**
     * 정상 PKCE 코드 교환으로 새 익스텐션 device session을 만들고 응답 JSON을 돌려준다.
     * {accessToken, refreshToken, expiresIn} 형태를 기대한다.
     */
    protected JsonNode exchangeNewDeviceSession(String email, String name) throws Exception {
        String sub = "ext-sub-" + UUID.randomUUID();
        stubGoogleTokenExchangeSuccess("fake-google-access-token-" + UUID.randomUUID());
        stubGoogleUserInfo(sub, email, name);

        String verifier = newCodeVerifier();
        String requestBody = "{"
                + "\"code\":\"fake-auth-code\","
                + "\"codeVerifier\":\"" + verifier + "\","
                + "\"redirectUri\":\"https://test-extension-id.chromiumapp.org/\""
                + "}";

        MvcResult result = mockMvc.perform(post("/api/extension/oauth/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
