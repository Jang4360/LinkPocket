package com.linkpocket.link;

import com.linkpocket.common.error.DomainException;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.Locale;

final class CanonicalUrlNormalizer {

    private static final Set<String> TRACKING_PARAMETERS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "gclid", "fbclid", "msclkid", "mc_eid"
    );

    String normalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw invalidUrl();
        }

        final URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException exception) {
            throw invalidUrl();
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || !isHttpScheme(scheme)) {
            throw invalidUrl();
        }

        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.startsWith("www.")) {
            normalizedHost = normalizedHost.substring("www.".length());
        }

        String path = uri.getRawPath();
        if (path != null && path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        StringBuilder canonical = new StringBuilder(normalizedScheme).append("://");
        if (uri.getRawUserInfo() != null) {
            canonical.append(uri.getRawUserInfo()).append('@');
        }
        canonical.append(normalizedHost);
        if (uri.getPort() != -1) {
            canonical.append(':').append(uri.getPort());
        }
        if (path != null) {
            canonical.append(path);
        }
        String query = removeTrackingParameters(uri.getRawQuery());
        if (query != null) {
            canonical.append('?').append(query);
        }
        if (uri.getRawFragment() != null) {
            canonical.append('#').append(uri.getRawFragment());
        }
        return canonical.toString();
    }

    private String removeTrackingParameters(String rawQuery) {
        if (rawQuery == null) {
            return null;
        }

        String filteredQuery = Arrays.stream(rawQuery.split("&", -1))
                .filter(parameter -> !TRACKING_PARAMETERS.contains(parameterName(parameter)))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");

        return filteredQuery.isEmpty() ? null : filteredQuery;
    }

    private String parameterName(String rawParameter) {
        int separator = rawParameter.indexOf('=');
        return separator == -1 ? rawParameter : rawParameter.substring(0, separator);
    }

    private boolean isHttpScheme(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private DomainException invalidUrl() {
        return new DomainException(LinkErrorCode.LINK_INVALID_URL);
    }
}
