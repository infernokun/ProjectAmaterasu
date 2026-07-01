package com.infernokun.amaterasu.controllers;

import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.proxmox.ProxmoxNetwork;
import com.infernokun.amaterasu.models.proxmox.ProxmoxNetworkAdapter;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVMConfig;
import com.infernokun.amaterasu.services.entity.lab.RemoteServerService;
import com.infernokun.amaterasu.services.alt.ProxmoxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/proxmox")
public class ProxmoxController {
    private final ProxmoxService proxmoxService;
    private final RemoteServerService remoteServerService;

    @GetMapping("/vms")
    public ResponseEntity<ApiResponse<List<ProxmoxVM>>> getVMs(@RequestParam(name = "template",
                                                                       required = false) Boolean template,
                                                               @RequestParam("remoteServerId") String remoteServerId) {
        RemoteServer remoteServer = remoteServerService.findServerById(remoteServerId);
        if (template != null && template) {
            return ResponseEntity.ok(ApiResponse.<List<ProxmoxVM>>builder()
                    .code(HttpStatus.OK.value())
                    .message("Retrieved templateVMs!")
                    .data(proxmoxService.getVMTemplates(remoteServer))
                    .build());
        }
        return ResponseEntity.ok(ApiResponse.<List<ProxmoxVM>>builder()
                .code(HttpStatus.OK.value())
                .message("Retrieved templateVMs!")
                .data(proxmoxService.getVMs(remoteServer))
                .build());
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<ProxmoxVMConfig>> getVmConfig(@RequestParam Integer vmid,
                                                                    @RequestParam("remoteServerId") String remoteServerId) {
        RemoteServer remoteServer = remoteServerService.findServerById(remoteServerId);
        return ResponseEntity.ok(ApiResponse.<ProxmoxVMConfig>builder()
                .code(HttpStatus.OK.value())
                .message("Retrieved templateVMs!")
                .data(proxmoxService.getVmConfigById(remoteServer, vmid))
                .build());
    }

    @GetMapping("/networks")
    public ResponseEntity<ApiResponse<List<ProxmoxNetwork>>> getVmConfig(
                                                                    @RequestParam("remoteServerId") String remoteServerId) {
        RemoteServer remoteServer = remoteServerService.findServerById(remoteServerId);
        return ResponseEntity.ok(ApiResponse.<List<ProxmoxNetwork>>builder()
                .code(HttpStatus.OK.value())
                .message("Retrieved templateVMs!")
                .data(proxmoxService.getNodeNetworks(remoteServer))
                .build());
    }

    @GetMapping("/adapters")
    public ResponseEntity<ApiResponse<List<ProxmoxNetworkAdapter>>> getNetworkAdapters(
            @RequestParam("remoteServerId") String remoteServerId) {
        RemoteServer remoteServer = remoteServerService.findServerById(remoteServerId);
        return ResponseEntity.ok(ApiResponse.<List<ProxmoxNetworkAdapter>>builder()
                .code(HttpStatus.OK.value())
                .message("Retrieved selectable network adapters!")
                .data(proxmoxService.listNetworkAdapters(remoteServer))
                .build());
    }

    @GetMapping("/available-ips")
    public ResponseEntity<ApiResponse<List<String>>> getAvailableIps(
            @RequestParam("remoteServerId") String remoteServerId,
            @RequestParam("bridge") String bridge,
            @RequestParam(name = "count", required = false, defaultValue = "1") Integer count) {
        RemoteServer remoteServer = remoteServerService.findServerById(remoteServerId);
        return ResponseEntity.ok(ApiResponse.<List<String>>builder()
                .code(HttpStatus.OK.value())
                .message("Retrieved available IPs!")
                .data(proxmoxService.getAvailableIps(remoteServer, bridge, count))
                .build());
    }
}