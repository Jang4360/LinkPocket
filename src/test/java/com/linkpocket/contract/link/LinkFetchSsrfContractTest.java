package com.linkpocket.contract.link;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약: plan/03-safe-fetch-extract.md AC + ADR-011 결정 1(SSRF 방어 깊이)
 *
 * 이 테스트가 쓰는 IP들은 AbstractLinkFetchContractTest의 test-only 허용 목록(WireMock의
 * 정확한 host, 127.0.0.1)과 다른 주소다 — loopback 대역(127.0.0.0/8) 전체가 아니라 딱 하나만
 * 열어뒀다는 걸 여기서 검증한다(127.0.0.2는 여전히 차단돼야 정상).
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - hostname을 실제로 연결하기 전에 해석된 IP가 사설(RFC1918)·loopback·link-local·
 *    클라우드 메타데이터(169.254.169.254) 대역이면 연결 자체를 시도하지 않고
 *    link.status='FAILED', failure_reason='SSRF_BLOCKED'로 전이
 *  - redirect Location의 host도 같은 검증을 거친다 — 차단 대역이면 그 redirect를
 *    따라가지 않고 SSRF_BLOCKED로 전이(원래 요청은 허용된 origin이어도 무관)
 */
class LinkFetchSsrfContractTest extends AbstractLinkFetchContractTest {

    @Test
    void loopback_address_other_than_test_origin_is_blocked() throws Exception {
        UUID linkId = seedPendingLink("http://127.0.0.2/anything");
        linkFetchService.fetchAndExtract(linkId);

        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("FAILED");
        assertThat(link.get("failure_reason")).isEqualTo("SSRF_BLOCKED");
    }

    @Test
    void cloud_metadata_address_is_blocked() throws Exception {
        UUID linkId = seedPendingLink("http://169.254.169.254/latest/meta-data/");
        linkFetchService.fetchAndExtract(linkId);

        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("FAILED");
        assertThat(link.get("failure_reason")).isEqualTo("SSRF_BLOCKED");
    }

    @Test
    void private_rfc1918_address_is_blocked() throws Exception {
        UUID linkId = seedPendingLink("http://10.0.0.5/internal");
        linkFetchService.fetchAndExtract(linkId);

        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("FAILED");
        assertThat(link.get("failure_reason")).isEqualTo("SSRF_BLOCKED");
    }

    @Test
    void redirect_to_blocked_address_is_not_followed() throws Exception {
        ORIGIN.stubFor(get(urlEqualTo("/redirect-to-internal"))
                .willReturn(aResponse().withStatus(302)
                        .withHeader("Location", "http://169.254.169.254/latest/meta-data/")));

        UUID linkId = seedPendingLink(originUrl("/redirect-to-internal"));
        linkFetchService.fetchAndExtract(linkId);

        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("FAILED");
        assertThat(link.get("failure_reason")).isEqualTo("SSRF_BLOCKED");
    }
}
