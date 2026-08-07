package com.linkpocket.link;

import org.apache.hc.client5.http.DnsResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SafeDnsResolver implements DnsResolver {

    private final Set<String> allowedTestHosts;

    public SafeDnsResolver(@Value("${linkpocket.fetch.ssrf.allowed-test-hosts:}") String allowedTestHosts) {
        this.allowedTestHosts = Arrays.stream(allowedTestHosts.split(","))
                .map(String::trim)
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (allowedTestHosts.contains(host)) {
            return addresses;
        }
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new BlockedAddressException(host);
            }
        }
        return addresses;
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return host;
    }

    static boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 16) {
            int first = Byte.toUnsignedInt(bytes[0]);
            return first == 0xfc || first == 0xfd;
        }
        if (bytes.length != 4) {
            return true;
        }

        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return first == 10
                || first == 127
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 169 && second == 254);
    }

    static final class BlockedAddressException extends UnknownHostException {

        BlockedAddressException(String host) {
            super("Blocked address for host: " + host);
        }
    }
}
