package com.infernokun.amaterasu.models.proxmox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProxmoxVMTest {

    private static ProxmoxVM tracked(String role) {
        return ProxmoxVM.builder().vmid(105).role(role).guestAgent(true).ready(true)
                .accessAddress("10.0.0.42").labSubnet("172.16.0.0/24").build();
    }

    private static ProxmoxVM current(String status) {
        return ProxmoxVM.builder().vmid(105).status(status).build();
    }

    @Test
    void aStoppedBastionForgetsItsAddressSoTheNextStartReadsItAgain() {
        ProxmoxVM vm = current("stopped").keepLabDetails(tracked(ProxmoxVM.BASTION));

        assertThat(vm.getAccessAddress()).isNull();
        assertThat(vm.getReady()).isFalse();
        assertThat(vm.getLabSubnet()).isEqualTo("172.16.0.0/24");
    }

    @Test
    void aRunningBastionKeepsItsAddress() {
        ProxmoxVM vm = current("running").keepLabDetails(tracked(ProxmoxVM.BASTION));

        assertThat(vm.getAccessAddress()).isEqualTo("10.0.0.42");
        assertThat(vm.getReady()).isTrue();
    }

    @Test
    void anotherRoleKeepsWhatItHadWhileStopped() {
        ProxmoxVM vm = current("stopped").keepLabDetails(tracked(null));

        assertThat(vm.getAccessAddress()).isEqualTo("10.0.0.42");
    }
}
