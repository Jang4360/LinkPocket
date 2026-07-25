package com.linkpocket.contract.link;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약: plan/03-safe-fetch-extract.md AC(fetch 성공·본문 추출 실패·실패 시 row 보존)
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - LinkFetchService.fetchAndExtract(UUID linkId)
 *    - fetch 성공 + 본문 추출 성공 → link.status='FETCHED', extracted_title/extracted_body 채움
 *    - fetch는 성공했지만 본문 블록을 못 찾으면 → link.status='READY_WITHOUT_CONTENT',
 *      failure_reason='EXTRACTION_FAILED', url은 그대로 보존
 *  - Flyway V5: link에 failure_reason text, fetched_at timestamptz,
 *    extracted_title text, extracted_body text 컬럼 추가. status는 CHECK 제약 없이 자유 문자열.
 */
class LinkFetchSuccessContractTest extends AbstractLinkFetchContractTest {

    @Test
    void successful_fetch_extracts_title_and_body_and_transitions_to_fetched() throws Exception {
        ORIGIN.stubFor(get(urlEqualTo("/article"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/html")
                        .withBody("""
                                <html><head><title>Real Article</title></head>
                                <body><nav>menu</nav><article><h1>Real Article</h1>
                                <p>This is a real, sufficiently long paragraph of article content
                                that a content extractor should recognize as the main body text
                                of the page, distinct from navigation or boilerplate chrome.</p>
                                </article><footer>copy</footer></body></html>
                                """)));

        UUID linkId = seedPendingLink(originUrl("/article"));
        linkFetchService.fetchAndExtract(linkId);

        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("FETCHED");
        assertThat((String) link.get("extracted_title")).isNotBlank();
        assertThat((String) link.get("extracted_body")).isNotBlank();
        assertThat(link.get("fetched_at")).isNotNull();
    }

    @Test
    void fetch_success_with_no_extractable_content_becomes_ready_without_content() throws Exception {
        ORIGIN.stubFor(get(urlEqualTo("/empty"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/html")
                        .withBody("<html><head><title>Empty</title></head><body></body></html>")));

        UUID linkId = seedPendingLink(originUrl("/empty"));
        linkFetchService.fetchAndExtract(linkId);

        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("READY_WITHOUT_CONTENT");
        assertThat(link.get("failure_reason")).isEqualTo("EXTRACTION_FAILED");
        assertThat(link.get("url"))
                .as("추출 실패해도 원본 URL은 보존돼야 한다")
                .isEqualTo(originUrl("/empty"));
    }
}
