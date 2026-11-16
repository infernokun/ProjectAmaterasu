package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVMConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** What a deploy records about each clone before it starts. */
class ProxmoxLabCopyTest {

    private static ProxmoxVMConfig config(String tags) {
        ProxmoxVMConfig config = new ProxmoxVMConfig();
        config.setTags(tags);
        config.setCiuser("player");
        config.setDynamicProperties("net0", "virtio=BC:24:11:D1:F1:8C,bridge=vmbr0");
        config.setDynamicProperties("net1", "virtio=BC:24:11:3D:72:44,bridge=vmbr1");
        config.setDynamicProperties("ipconfig1", "ip=172.16.0.1/24");
        return config;
    }

    private static ProxmoxVM template() {
        return ProxmoxVM.builder().vmid(9005).name("tpl-bastion").template(true).build();
    }

    @Test
    void aBastionCopyTakesTheSshPortFromItsTemplatesTag() {
        ProxmoxVM copy = ProxmoxService.labCopy(105, template(), config("amaterasu;bastion;ssh-port-2022"));

        assertThat(copy.isBastion()).isTrue();
        assertThat(copy.getAccessPort()).isEqualTo(2022);
        assertThat(copy.getUplinkBridge()).isEqualTo("vmbr0");
        assertThat(copy.getLabSubnet()).isEqualTo("172.16.0.0/24");
    }

    @Test
    void aBastionWithoutTheTagUsesPort22() {
        assertThat(ProxmoxService.labCopy(105, template(), config("amaterasu;bastion")).getAccessPort()).isNull();
    }

    @Test
    void onlyBastionsGetAPort() {
        assertThat(ProxmoxService.labCopy(106, template(), config("amaterasu;ssh-port-2022")).getAccessPort()).isNull();
    }
}
