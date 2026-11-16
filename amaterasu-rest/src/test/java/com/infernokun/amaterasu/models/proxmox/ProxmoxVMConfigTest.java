package com.infernokun.amaterasu.models.proxmox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProxmoxVMConfigTest {

    private static ProxmoxVMConfig tagged(String tags) {
        ProxmoxVMConfig config = new ProxmoxVMConfig();
        config.setTags(tags);
        return config;
    }

    @Test
    void anSshPortTagGivesTheBastionsPort() {
        assertThat(tagged("amaterasu;bastion;ssh-port-2022").getSshPort()).isEqualTo(2022);
        assertThat(tagged("bastion, SSH-PORT-2200").getSshPort()).isEqualTo(2200);
    }

    @Test
    void withoutATagOrWithPort22TheDefaultStands() {
        assertThat(tagged("amaterasu;bastion").getSshPort()).isNull();
        assertThat(tagged(null).getSshPort()).isNull();
        assertThat(tagged("bastion;ssh-port-22").getSshPort()).isNull();
    }

    @Test
    void anImpossiblePortIsIgnored() {
        assertThat(tagged("bastion;ssh-port-70000").getSshPort()).isNull();
        assertThat(tagged("bastion;ssh-port-0").getSshPort()).isNull();
        assertThat(tagged("bastion;ssh-port-abc").getSshPort()).isNull();
    }
}
