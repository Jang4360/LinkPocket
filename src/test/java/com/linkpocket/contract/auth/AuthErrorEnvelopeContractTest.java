package com.linkpocket.contract.auth;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 계약: plan/01-auth-google-oauth.md "에러 코드 계약" + architecture/api-error-contract.md
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - 공통 ErrorCode 인터페이스 + 공통 JSON envelope({code,domain,message,traceId,details})
 *  - DomainException + 전역 @RestControllerAdvice 예외 핸들러
 *  - AuthErrorCode enum (모든 상수가 "AUTH_" 접두사)
 *  - 인증 필요한 보호 엔드포인트 GET /api/me, 인증 없으면 401 + AUTH_SESSION_INVALID
 *  - 서버 500도 이 envelope 형태로 나가고 stack trace를 노출하지 않아야 한다
 */
class AuthErrorEnvelopeContractTest extends AbstractAuthContractTest {

    @Test
    void unauthenticated_me_returns_401_with_error_envelope() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_INVALID"))
                .andExpect(jsonPath("$.domain").value("AUTH"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void unknown_route_does_not_leak_stack_trace() throws Exception {
        mockMvc.perform(get("/api/this-route-does-not-exist"))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    org.assertj.core.api.Assertions.assertThat(body)
                            .doesNotContain("Exception")
                            .doesNotContain("\tat ");
                });
    }
}
