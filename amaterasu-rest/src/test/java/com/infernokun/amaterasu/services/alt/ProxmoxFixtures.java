package com.infernokun.amaterasu.services.alt;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Response bodies read from a PVE 9.2.2 node (node proxmox2), under test resources proxmox/. */
final class ProxmoxFixtures {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ProxmoxFixtures() {
    }

    static String body(String name) {
        try (InputStream in = ProxmoxFixtures.class.getResourceAsStream("/proxmox/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("No fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The node's real interfaces plus the given lab bridges, with a pending diff when {@code changes} is set. */
    @SuppressWarnings("unchecked")
    static String network(String changes, String... bridges) {
        try {
            Map<String, Object> body = new LinkedHashMap<>(JSON.readValue(body("network.json"), Map.class));
            List<Object> data = new ArrayList<>((List<Object>) body.get("data"));
            for (String bridge : bridges) {
                data.add(Map.of("iface", bridge, "type", "bridge", "active", 0, "autostart", 1,
                        "bridge_ports", "", "comments", "amaterasu lab lt1\n"));
            }
            body.put("data", data);
            if (changes != null) {
                body.put("changes", changes);
            }
            return JSON.writeValueAsString(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A pending diff as PVE reports it: diff -u of interfaces against interfaces.new. */
    static String diff(String... hunkLines) {
        return "--- /etc/network/interfaces\t2026-11-06 05:00:00.000000000 +0000\n"
                + "+++ /etc/network/interfaces.new\t2026-11-06 05:40:00.000000000 +0000\n"
                + String.join("\n", hunkLines) + "\n";
    }

    static String addedBridge(String bridge) {
        return String.join("\n",
                "@@ -18,3 +18,10 @@",
                " \tbridge-stp off",
                " \tbridge-fd 0",
                " ",
                "+auto " + bridge,
                "+iface " + bridge + " inet manual",
                "+\tbridge-ports none",
                "+\tbridge-stp off",
                "+\tbridge-fd 0",
                "+#amaterasu lab lt1",
                "+");
    }

    static String removedBridge(String bridge) {
        return String.join("\n",
                "@@ -18,10 +18,3 @@",
                " \tbridge-stp off",
                " \tbridge-fd 0",
                " ",
                "-auto " + bridge,
                "-iface " + bridge + " inet manual",
                "-\tbridge-ports none",
                "-\tbridge-stp off",
                "-\tbridge-fd 0",
                "-#amaterasu lab lt1",
                "-");
    }

    static String changedVmbr0Address() {
        return String.join("\n",
                "@@ -10,7 +10,7 @@",
                " ",
                " auto vmbr0",
                " iface vmbr0 inet static",
                "-\taddress 10.0.0.205/24",
                "+\taddress 10.0.0.206/24",
                " \tgateway 10.0.0.1",
                " \tbridge-ports nic0",
                " \tbridge-stp off");
    }
}
