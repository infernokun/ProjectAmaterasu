package com.infernokun.amaterasu.models.proxmox;

/** What a node's vncproxy call returns: the port it listens on and a one-time ticket. */
public record ProxmoxVncTicket(String port, String ticket, String user) {
}
