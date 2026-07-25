package com.linkpocket.contract.link;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약: plan/03-safe-fetch-extract.md AC + ADR-011 결정 2(timeout 구조)·결정 3(재시도·크기 제한)
 *
 * timeout 예산은 AbstractLinkFetchContractTest의 @DynamicPropertySource가 테스트용으로
 * 작게(connect 300ms/response 500ms/read 1000ms) 오버라이드해뒀다 — 운영 기본값(3000/5000/
 * 15000ms)과 같은 프로퍼티 키를 Codex가 실제로 읽어야 이 오버라이드가 의미가 있다.
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - timeout 초과 시 최대 2회까지 재시도(총 3회 시도) 후에도 실패하면 FAILED/FETCH_TIMEOUT
 *  - 429/5xx도 같은 재시도 정책(최대 2회, 총 3회 시도) 후 실패하면 FAILED/HTTP_SERVER_ERROR
 *  - 4xx는 재시도 없이 즉시 FAILED/HTTP_CLIENT_ERROR(총 1회 시도)
 *  - 압축 해제 후 응답 본문이 5MB를 넘으면 그 시점에서 중단하고 FAILED/CONTENT_TOO_LARGE
 *    (Content-Length 헤더만 보고 판단하지 않는다 — 실제로 읽은 바이트 수 기준)
 */
class LinkFetchRetryAndLimitsContractTest extends AbstractLinkFetchContractTest {

    @Test
    void timeout_is_retried_then_fails() throws Exception {
        ORIGIN.stubFor(get(urlEqualTo("/slow"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(1500)
                        .withHeader("Content-Type", "text/html").withBody("<html></html>")));

        UUID linkId = seedPendingLink(originUrl("/slow"));
        linkFetchService.fetchAndExtract(linkId);

        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("FAILED");
        assertThat(link.get("failure_reason")).isEqualTo("FETCH_TIMEOUT");
        ORIGIN.verify(3, getRequestedFor(urlEqualTo("/slow")));
    }

    @Test
    void server_error_is_retried_up_to_two_times_then_fails() throws Exception {
        ORIGIN.stubFor(get(urlEqualTo("/always-500")).willReturn(aResponse().withStatus(500)));

        UUID linkId = seedPendingLink(originUrl("/always-500"));
        linkFetchService.fetchAndExtract(linkId);

        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("FAILED");
        assertThat(link.get("failure_reason")).isEqualTo("HTTP_SERVER_ERROR");
        ORIGIN.verify(3, getRequestedFor(urlEqualTo("/always-500")));
    }

    @Test
    void client_error_is_not_retried() throws Exception {
        ORIGIN.stubFor(get(urlEqualTo("/not-found")).willReturn(aResponse().withStatus(404)));

        UUID linkId = seedPendingLink(originUrl("/not-found"));
        linkFetchService.fetchAndExtract(linkId);

        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("FAILED");
        assertThat(link.get("failure_reason")).isEqualTo("HTTP_CLIENT_ERROR");
        ORIGIN.verify(1, getRequestedFor(urlEqualTo("/not-found")));
    }

    @Test
    void body_over_five_megabytes_is_rejected() throws Exception {
        String oversizedBody = "a".repeat(5 * 1024 * 1024 + 1024);
        ORIGIN.stubFor(get(urlEqualTo("/huge"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/html")
                        .withBody(oversizedBody)));

        UUID linkId = seedPendingLink(originUrl("/huge"));
        linkFetchService.fetchAndExtract(linkId);

        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("FAILED");
        assertThat(link.get("failure_reason")).isEqualTo("CONTENT_TOO_LARGE");
    }
}
