package com.infernokun.amaterasu.controllers;

import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.ProxmoxVM;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.templates.ProxmoxRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/proxmox")
public class ProxmoxController {
    private final ProxmoxRestTemplate proxmoxService;

    public ProxmoxController(ProxmoxRestTemplate proxmoxService) {
        this.proxmoxService = proxmoxService;
    }

    @GetMapping("/vms")
    public ResponseEntity<ApiResponse<List<ProxmoxVM>>> getVMs(@RequestParam(name = "template", required = false) Boolean template) {
        if (template != null && template) {
            return ResponseEntity.ok(ApiResponse.<List<ProxmoxVM>>builder()
                    .code(HttpStatus.OK.value())
                    .message("Retrieved templateVMs!")
                    .data(proxmoxService.getVMTemplates())
                    .build());
        }
        return ResponseEntity.ok(ApiResponse.<List<ProxmoxVM>>builder()
                .code(HttpStatus.OK.value())
                .message("Retrieved templateVMs!")
                .data(proxmoxService.getVMs())
                .build());
    }
}