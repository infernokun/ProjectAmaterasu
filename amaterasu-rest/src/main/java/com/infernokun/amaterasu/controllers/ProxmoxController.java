package com.infernokun.amaterasu.controllers;

import com.infernokun.amaterasu.models.ProxmoxVM;
import com.infernokun.amaterasu.templates.ProxmoxRestTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public List<ProxmoxVM> getVMs() {
        return proxmoxService.getVMs();
    }

    @GetMapping("/templates")
    public List<ProxmoxVM> getTemplates() {
        return proxmoxService.getTemplates();
    }
}