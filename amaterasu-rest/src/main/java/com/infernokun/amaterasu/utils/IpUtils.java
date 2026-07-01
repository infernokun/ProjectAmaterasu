package com.infernokun.amaterasu.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small helper for the IPv4 math the Proxmox deploy path needs: turning a bridge
 * CIDR into the list of assignable host addresses and pulling the prefix length /
 * gateway out of the node's network definition. Kept deliberately dependency-free.
 */
public final class IpUtils {
    private static final Pattern IPV4 = Pattern.compile("\\b(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\b");

    private IpUtils() {
    }

    /** Extracts the prefix length from a CIDR string like "10.0.0.1/24"; null if absent/invalid. */
    public static Integer prefixFromCidr(String cidr) {
        if (cidr == null || !cidr.contains("/")) {
            return null;
        }
        try {
            int prefix = Integer.parseInt(cidr.substring(cidr.indexOf('/') + 1).trim());
            return (prefix >= 0 && prefix <= 32) ? prefix : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Returns the first IPv4 literal found in the text (e.g. the "ip=" part of an ipconfig). */
    public static String firstIpv4(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = IPV4.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static long toLong(String ip) {
        String[] octets = ip.trim().split("\\.");
        if (octets.length != 4) {
            throw new IllegalArgumentException("Not an IPv4 address: " + ip);
        }
        long value = 0;
        for (String octet : octets) {
            int part = Integer.parseInt(octet);
            if (part < 0 || part > 255) {
                throw new IllegalArgumentException("Not an IPv4 address: " + ip);
            }
            value = (value << 8) | part;
        }
        return value;
    }

    private static String fromLong(long value) {
        return String.format("%d.%d.%d.%d",
                (value >> 24) & 0xff,
                (value >> 16) & 0xff,
                (value >> 8) & 0xff,
                value & 0xff);
    }

    /**
     * Enumerates the usable host addresses of the subnet that {@code cidr} belongs to,
     * excluding the network and broadcast addresses. A {@code limit <= 0} returns every host.
     * Returns an empty list when the CIDR is missing/invalid so callers can fall back cleanly.
     */
    public static List<String> hostAddresses(String cidr, int limit) {
        List<String> hosts = new ArrayList<>();
        if (cidr == null) {
            return hosts;
        }
        Integer prefix = prefixFromCidr(cidr);
        String base = cidr.contains("/") ? cidr.substring(0, cidr.indexOf('/')) : cidr;
        if (prefix == null || prefix >= 31) {
            // /31 and /32 have no conventional usable host range for our purposes.
            return hosts;
        }
        try {
            long ip = toLong(base);
            long mask = prefix == 0 ? 0 : (0xffffffffL << (32 - prefix)) & 0xffffffffL;
            long network = ip & mask;
            long broadcast = network | (~mask & 0xffffffffL);
            for (long addr = network + 1; addr < broadcast; addr++) {
                hosts.add(fromLong(addr));
                if (limit > 0 && hosts.size() >= limit) {
                    break;
                }
            }
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
        return hosts;
    }
}
