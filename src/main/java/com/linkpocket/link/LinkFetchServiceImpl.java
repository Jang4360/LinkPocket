package com.linkpocket.link;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class LinkFetchServiceImpl implements LinkFetchService {

    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_CONTENT_BYTES = 5 * 1024 * 1024;

    private final JdbcTemplate jdbcTemplate;
    private final ContentExtractor contentExtractor = new ContentExtractor();
    private final Set<String> allowedTestHosts;
    private final int connectTimeoutMillis;
    private final int responseTimeoutMillis;
    private final int readTimeoutMillis;

    public LinkFetchServiceImpl(
            JdbcTemplate jdbcTemplate,
            @Value("${linkpocket.fetch.ssrf.allowed-test-hosts:}") String allowedTestHosts,
            @Value("${linkpocket.fetch.timeout.connect-ms:3000}") int connectTimeoutMillis,
            @Value("${linkpocket.fetch.timeout.response-ms:5000}") int responseTimeoutMillis,
            @Value("${linkpocket.fetch.timeout.read-ms:15000}") int readTimeoutMillis
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.allowedTestHosts = Arrays.stream(allowedTestHosts.split(","))
                .map(String::trim)
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.responseTimeoutMillis = responseTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    @Override
    public void fetchAndExtract(UUID linkId) {
        int claimed = jdbcTemplate.update(
                "update link set status = 'FETCHING' where id = ? and status = 'PENDING'",
                linkId
        );
        if (claimed == 0) {
            return;
        }

        String url = jdbcTemplate.queryForObject("select url from link where id = ?", String.class, linkId);
        try {
            FetchedPage page = fetchWithRetry(URI.create(url));
            Optional<ContentExtractor.ExtractedContent> extracted = contentExtractor.extract(page.url().toString(), page.html());
            if (extracted.isPresent()) {
                ContentExtractor.ExtractedContent content = extracted.get();
                markFetched(linkId, content);
            } else {
                markExtractionFailed(linkId);
            }
        } catch (FetchException exception) {
            markFailed(linkId, exception.reason());
        } catch (IllegalArgumentException exception) {
            markFailed(linkId, FetchFailureReason.HTTP_CLIENT_ERROR);
        }
    }

    private FetchedPage fetchWithRetry(URI initialUri) throws FetchException {
        FetchException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return fetchOnce(initialUri);
            } catch (FetchException exception) {
                lastFailure = exception;
                if (!exception.retryable() || attempt == MAX_ATTEMPTS) {
                    throw exception;
                }
                backoff(attempt);
            }
        }
        throw lastFailure;
    }

    private FetchedPage fetchOnce(URI initialUri) throws FetchException {
        URI currentUri = initialUri;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            assertSafeTarget(currentUri);
            try (CloseableHttpClient client = newHttpClient()) {
                ClassicHttpResponse response = client.executeOpen(null,
                        new org.apache.hc.client5.http.classic.methods.HttpGet(currentUri), null);
                try (response) {
                    int status = response.getCode();
                    if (isRedirect(status)) {
                        Header location = response.getFirstHeader("Location");
                        EntityUtils.consumeQuietly(response.getEntity());
                        if (location == null) {
                            throw new FetchException(FetchFailureReason.HTTP_CLIENT_ERROR, false);
                        }
                        currentUri = currentUri.resolve(location.getValue());
                        continue;
                    }
                    if (status == 429 || status >= 500) {
                        throw new FetchException(FetchFailureReason.HTTP_SERVER_ERROR, true);
                    }
                    if (status >= 400) {
                        throw new FetchException(FetchFailureReason.HTTP_CLIENT_ERROR, false);
                    }
                    if (status < 200 || status >= 300) {
                        throw new FetchException(FetchFailureReason.HTTP_CLIENT_ERROR, false);
                    }
                    return new FetchedPage(currentUri, readBody(response.getEntity().getContent()));
                }
            } catch (SocketTimeoutException exception) {
                throw new FetchException(FetchFailureReason.FETCH_TIMEOUT, true);
            } catch (IOException exception) {
                if (hasTimeoutCause(exception)) {
                    throw new FetchException(FetchFailureReason.FETCH_TIMEOUT, true);
                }
                throw new FetchException(FetchFailureReason.HTTP_SERVER_ERROR, true);
            }
        }
        throw new FetchException(FetchFailureReason.HTTP_CLIENT_ERROR, false);
    }

    private CloseableHttpClient newHttpClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMillis))
                .setResponseTimeout(Timeout.ofMilliseconds(responseTimeoutMillis))
                .build();
        return HttpClients.custom()
                .disableRedirectHandling()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultSocketConfig(org.apache.hc.core5.http.io.SocketConfig.custom()
                                .setSoTimeout(Timeout.ofMilliseconds(readTimeoutMillis))
                                .build())
                        .build())
                .build();
    }

    private String readBody(InputStream inputStream) throws IOException, FetchException {
        try (inputStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_CONTENT_BYTES) {
                    throw new FetchException(FetchFailureReason.CONTENT_TOO_LARGE, false);
                }
                output.write(buffer, 0, read);
            }
            return output.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private void assertSafeTarget(URI uri) throws FetchException {
        String host = uri.getHost();
        String scheme = uri.getScheme();
        if (host == null || scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new FetchException(FetchFailureReason.HTTP_CLIENT_ERROR, false);
        }
        if (allowedTestHosts.contains(host)) {
            return;
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isBlockedAddress(address)) {
                    throw new FetchException(FetchFailureReason.SSRF_BLOCKED, false);
                }
            }
        } catch (UnknownHostException exception) {
            throw new FetchException(FetchFailureReason.HTTP_CLIENT_ERROR, false);
        }
    }

    private boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) {
            return false;
        }
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return first == 10
                || first == 127
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 169 && second == 254);
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void backoff(int attempt) {
        long baseDelayMillis = 25L * (1L << (attempt - 1));
        long jitterMillis = ThreadLocalRandom.current().nextLong(baseDelayMillis + 1);
        try {
            Thread.sleep(baseDelayMillis + jitterMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void markFetched(UUID linkId, ContentExtractor.ExtractedContent content) {
        jdbcTemplate.update(
                """
                        update link
                        set status = 'FETCHED', failure_reason = null, fetched_at = now(),
                            extracted_title = ?, extracted_body = ?
                        where id = ?
                        """,
                content.title(), content.body(), linkId
        );
    }

    private void markExtractionFailed(UUID linkId) {
        jdbcTemplate.update(
                """
                        update link
                        set status = 'READY_WITHOUT_CONTENT', failure_reason = 'EXTRACTION_FAILED',
                            fetched_at = now(), extracted_title = null, extracted_body = null
                        where id = ?
                        """,
                linkId
        );
    }

    private void markFailed(UUID linkId, FetchFailureReason reason) {
        jdbcTemplate.update(
                "update link set status = 'FAILED', failure_reason = ? where id = ?",
                reason.name(), linkId
        );
    }

    private record FetchedPage(URI url, String html) {
    }

    private static final class FetchException extends Exception {
        private final FetchFailureReason reason;
        private final boolean retryable;

        private FetchException(FetchFailureReason reason, boolean retryable) {
            this.reason = reason;
            this.retryable = retryable;
        }

        private FetchFailureReason reason() {
            return reason;
        }

        private boolean retryable() {
            return retryable;
        }
    }
}
