package com.linkpocket.contract.link;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약: plan/03-safe-fetch-extract.md AC + ADR-011 결정 4(중복 fetch 방지)
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - fetchAndExtract 시작 시 `UPDATE link SET status='FETCHING' WHERE id=? AND status='PENDING'`
 *    같은 원자적 조건부 UPDATE로 선점한다. 영향받은 row가 0개면 이미 다른 호출이 처리
 *    중이라는 뜻이므로 실제 fetch를 시도하지 않고 즉시 반환한다(예외를 던지지 않는다).
 */
class LinkFetchConcurrencyContractTest extends AbstractLinkFetchContractTest {

    @Test
    void concurrent_fetch_calls_result_in_exactly_one_real_fetch() throws Exception {
        ORIGIN.stubFor(get(urlEqualTo("/racy"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><article><h1>t</h1><p>"
                                + "enough content to be recognized as an article body here."
                                + "</p></article></body></html>")));

        UUID linkId = seedPendingLink(originUrl("/racy"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> {
                await(startGate);
                linkFetchService.fetchAndExtract(linkId);
            });
            Future<?> second = executor.submit(() -> {
                await(startGate);
                linkFetchService.fetchAndExtract(linkId);
            });
            startGate.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }

        ORIGIN.verify(1, getRequestedFor(urlEqualTo("/racy")));
        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("FETCHED");
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
