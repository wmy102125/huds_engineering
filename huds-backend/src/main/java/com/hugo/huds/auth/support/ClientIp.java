package com.hugo.huds.auth.support;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;
/**
 * @author Meiyan Wang
 * @date 2026-10-16 11:02 a.m.
 * @description
 * Resolves the client IP used for rate limiting and audit.
 *
 * <p>{@code CF-Connecting-IP} is trusted only when the TCP peer is a loopback or private address
 * (Cloudflare Tunnel or a local reverse proxy). The origin server must not be reachable directly
 * from the internet, otherwise the header could be forged (see ADR-014).</p>
 */
public final class ClientIp {
    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

    private ClientIp() {
    }

    public static String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String header = request.getHeader("CF-Connecting-IP");
        if (header != null && IP_LITERAL.matcher(header.trim()).matches() && isInternalPeer(remote)) {
            return header.trim();
        }
        return remote;
    }

    private static boolean isInternalPeer(String remote) {
        if (remote == null || !IP_LITERAL.matcher(remote).matches()) {
            return false;
        }
        try {
            // The value is a validated IP literal, so no DNS lookup happens here.
            InetAddress address = InetAddress.getByName(remote);
            return address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
