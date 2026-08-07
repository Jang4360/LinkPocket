package com.linkpocket.contract.link;

import org.apache.hc.client5.http.DnsResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약: ADR-011 결정 1(SSRF 방어 깊이)의 구현이 실제로 그 설계를 지키는지 확인하는
 * 회귀 테스트 — plan-03 리뷰(2026-07-27, exp-06 준비 중 발견) 결과 코드에 확정된 구멍
 * 2개가 있었다:
 *
 *  1. DNS rebinding(TOCTOU): {@code assertSafeTarget}이 검증용으로 한 번 DNS를 조회하고,
 *     실제 연결은 HttpClient5의 커넥션 매니저가 **별도로 다시** DNS를 조회한다. 두 조회가
 *     같은 답을 준다는 보장이 없다 — 공격자가 1차 조회엔 공인 IP를, 2차 조회엔 사설 IP를
 *     주면 검증은 통과하고 연결은 내부로 간다.
 *  2. IPv6 미검사: {@code isBlockedAddress}가 4바이트(IPv4)만 검사하고 IPv6(16바이트)는
 *     길이 조건에서 무조건 안전으로 통과시킨다({@code ::1}, {@code fc00::/7}, {@code fe80::/10}
 *     전부 무방비).
 *
 * green으로 만들려면 Codex가 만들어야 하는 것:
 *  - {@code org.apache.hc.client5.http.DnsResolver} 구현체를 Spring 빈(`@Component`)으로
 *    등록하고, 검증(`assertSafeTarget`)과 실제 연결(`PoolingHttpClientConnectionManagerBuilder
 *    .setDnsResolver(...)`) **양쪽 모두** 이 같은 빈을 통해서만 DNS를 조회하게 배선한다 —
 *    검증용 조회와 연결용 조회가 분리되지 않아야 한다(결과적으로 이 테스트의 Fake로
 *    양쪽 다 대체되게).
 *  - {@code isBlockedAddress}(또는 동등 로직)가 IPv6 주소도 loopback({@code ::1})·
 *    link-local({@code fe80::/10})·unique-local({@code fc00::/7})을 차단하도록 확장한다.
 *    {@code InetAddress.isLoopbackAddress()/isLinkLocalAddress()}는 이미 IPv6도 인식하므로
 *    그 판정 뒤에 "IPv4가 아니면 무조건 통과"로 새는 지금의 length==4 분기를 없애면 된다.
 */
class LinkFetchSsrfAdvancedContractTest extends AbstractLinkFetchContractTest {

    @TestConfiguration
    static class FakeDnsResolverConfig {
        @Bean
        @Primary
        FakeDnsResolver fakeDnsResolver() {
            return new FakeDnsResolver();
        }
    }

    @Autowired
    private FakeDnsResolver fakeDnsResolver;

    @BeforeEach
    void resetResolver() {
        fakeDnsResolver.reset();
    }

    /**
     * allowedTestHosts는 WireMock의 실제 host만 예외로 두므로(AbstractLinkFetchContractTest
     * 문서 참고), 여기서 쓰는 hostname은 그 목록에 없는 **가짜 hostname**이어야 DNS 조회
     * 경로(assertSafeTarget → 이 fake resolver)를 실제로 통과한다. 포트는 WireMock의 실제
     * 동적 포트를 그대로 붙인다 — 실제 TCP 연결은 그 포트로 가야 하기 때문이다.
     */
    private String syntheticUrl(String path) {
        int port = URI.create(ORIGIN.baseUrl()).getPort();
        return "http://ssrf-test.invalid:" + port + path;
    }

    @Test
    void dns_rebinding_between_check_and_connect_does_not_leak_to_blocked_target() throws Exception {
        ORIGIN.stubFor(get(urlEqualTo("/rebind-target"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><article><h1>t</h1><p>"
                                + "enough content to be recognized as an article body here."
                                + "</p></article></body></html>")));

        InetAddress originAddress = InetAddress.getByName(URI.create(originUrl("/")).getHost());
        InetAddress blockedAddress = InetAddress.getByName("127.0.0.2");
        fakeDnsResolver.firstAnswer = new InetAddress[]{originAddress};
        fakeDnsResolver.subsequentAnswer = new InetAddress[]{blockedAddress};

        UUID linkId = seedPendingLink(syntheticUrl("/rebind-target"));
        linkFetchService.fetchAndExtract(linkId);

        Map<String, Object> link = loadLink(linkId);
        String status = String.valueOf(link.get("status"));
        assertThat(status).isIn("FETCHED", "FAILED");
        if ("FAILED".equals(status)) {
            assertThat(link.get("failure_reason"))
                    .as("검증 이후 연결 단계에서 별도로 DNS를 다시 조회해 blocked 주소로 샜다면, "
                            + "적어도 그 시점에 SSRF_BLOCKED로 잡혀야 한다 — 다른 사유(timeout 등)로 "
                            + "'우연히' 실패하면 실제로는 공격자가 지정한 사설 IP에 연결을 시도했다는 뜻이다.")
                    .isEqualTo("SSRF_BLOCKED");
        }
        assertThat(fakeDnsResolver.callCount.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void ipv6_loopback_address_is_blocked() throws Exception {
        fakeDnsResolver.fixedAnswer = new InetAddress[]{InetAddress.getByName("::1")};

        UUID linkId = seedPendingLink(syntheticUrl("/ipv6-loopback"));
        linkFetchService.fetchAndExtract(linkId);

        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("FAILED");
        assertThat(link.get("failure_reason")).isEqualTo("SSRF_BLOCKED");
    }

    @Test
    void ipv6_unique_local_address_is_blocked() throws Exception {
        fakeDnsResolver.fixedAnswer = new InetAddress[]{InetAddress.getByName("fc00::1")};

        UUID linkId = seedPendingLink(syntheticUrl("/ipv6-ula"));
        linkFetchService.fetchAndExtract(linkId);

        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("FAILED");
        assertThat(link.get("failure_reason")).isEqualTo("SSRF_BLOCKED");
    }

    @Test
    void ipv6_link_local_address_is_blocked() throws Exception {
        fakeDnsResolver.fixedAnswer = new InetAddress[]{InetAddress.getByName("fe80::1")};

        UUID linkId = seedPendingLink(syntheticUrl("/ipv6-link-local"));
        linkFetchService.fetchAndExtract(linkId);

        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("FAILED");
        assertThat(link.get("failure_reason")).isEqualTo("SSRF_BLOCKED");
    }

    @Test
    void multi_hop_redirect_chain_stops_at_first_blocked_hop() throws Exception {
        ORIGIN.stubFor(get(urlEqualTo("/chain-a"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", originUrl("/chain-b"))));
        ORIGIN.stubFor(get(urlEqualTo("/chain-b"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", "http://10.0.0.9/internal")));

        UUID linkId = seedPendingLink(originUrl("/chain-a"));
        linkFetchService.fetchAndExtract(linkId);

        Map<String, Object> link = loadLink(linkId);
        assertThat(link.get("status")).isEqualTo("FAILED");
        assertThat(link.get("failure_reason")).isEqualTo("SSRF_BLOCKED");
    }

    /** 테스트가 호출 순서·횟수·응답을 결정적으로 제어하는 DnsResolver fake. */
    static class FakeDnsResolver implements DnsResolver {
        final AtomicInteger callCount = new AtomicInteger();
        volatile InetAddress[] firstAnswer;
        volatile InetAddress[] subsequentAnswer;
        volatile InetAddress[] fixedAnswer;

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            int call = callCount.incrementAndGet();
            if (fixedAnswer != null) {
                return fixedAnswer;
            }
            if (call == 1 && firstAnswer != null) {
                return firstAnswer;
            }
            if (subsequentAnswer != null) {
                return subsequentAnswer;
            }
            return InetAddress.getAllByName(host);
        }

        @Override
        public String resolveCanonicalHostname(String host) {
            return host;
        }

        void reset() {
            callCount.set(0);
            firstAnswer = null;
            subsequentAnswer = null;
            fixedAnswer = null;
        }
    }
}
