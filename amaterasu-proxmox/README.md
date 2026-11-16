# amaterasu-proxmox

Proxmox tooling for ProjectAmaterasu: a small helper CLI for working with a node by hand, and an API mock the lab lifecycle can be developed and tested against without a real node.

## Helper CLI

`main.py` talks to the Proxmox API with an API token.

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `TOKEN_ID` | | API token id, for example `root@pam!amaterasu` |
| `TOKEN_SECRET` | | API token secret |
| `PROXMOX_HOST` | `https://10.0.0.250:8006` | Node URL |
| `PROXMOX_NODE` | `inferno` | Node name |
| `TEAM_ID` | `185CPT` | Prefix for VMs created by the demo flow |

Put them in a `.env` file next to `main.py` or export them, then:

```bash
uv venv && uv pip install --require-hashes -r requirements.txt
```

```bash
uv run --no-project python main.py --quick-delete --dry-run
```

With no arguments the CLI does nothing. `--quick-delete` stops and deletes every VM with less than 10 hours of uptime and an id above 120, after asking for confirmation; `--dry-run` only lists them.

Dependencies are pinned in `requirements.in` and hash-locked in `requirements.txt`; the mock's own are in `mock/requirements.in` and `mock/requirements.txt`, so the CLI's image doesn't carry them. Run `npm run deps:lock` from the repo root after changing an `.in` file.

## API mock

`mock/` is a stateful [aiohttp](https://docs.aiohttp.org/) server that answers every Proxmox call the REST service makes, the way the PVE 9.2.2 node it was built against does:

- Every `/api2/json` call needs an API token in the form the REST service sends (`PVEAPIToken=USER@REALM!TOKENID=SECRET`, or with a space); anything else gets 401, which the server check reports as a rejected token.
- A node name other than those `/nodes` lists (`inferno`, `pve1`, `pve2`) gets PVE's 500 "hostname lookup … failed", which the server check reports as "No node called …".
- Errors have PVE's body, `{"data":null,"message":"…\n"}`; a missing VM is the 500 "Configuration file '…/N.conf' does not exist".
- Clone, start, stop, shutdown, destroy, the asynchronous `POST …/config` and network reloads return a task UPID that finishes after `MOCK_TASK_MS` (default 3000). `PUT …/config` applies before it answers.
- Clones stay locked until their task finishes, and running VMs cannot be destroyed.
- Bridges can be created and deleted and take effect once network changes are applied. Until then `/nodes/{node}/network` carries the pending `changes` diff, and a VM whose NIC is on a bridge that isn't applied fails its start task with "bridge '…' does not exist".
- `/cluster/nextid` returns the lowest free VM id, or checks the one asked for with `?vmid=`.
- `/version`, `/nodes/{node}/status` and `/access/permissions` answer as the PVE 9.2.2 node does, with a token that holds every privilege Amaterasu checks for.
- VMs list like PVE's: `cpus`, `cpu` as a fraction, `mem` and `maxmem` in bytes, and an uptime that counts from the start. `status/current` adds `qmpstatus` and `agent`.
- A NIC written without a MAC (`virtio,bridge=abr0`) gets a fresh one, as PVE does, and `cipassword` reads back masked.
- `agent/network-get-interfaces` answers for VMs with `agent` enabled once they have run for `MOCK_AGENT_MS` (default 8000), with a LAN address on NICs outside the `abrN` lab bridges and the `ipconfigN` address (else one in 172.16.0.0/24) on lab NICs.
- `vncproxy` on a running VM returns a one-time ticket for the token's user, and `vncwebsocket` with that ticket serves a text console over RFB, so the web app's VM console can be tried end to end. Set `MOCK_HOST=0.0.0.0` when the API runs in a container.

Templates `9000` to `9004` and bridges `vmbr0` and `vmbr1` are seeded from `mock/data`. `9004` has the guest agent, and `9005` is `tpl-bastion` as on the real node: tagged `bastion`, cloud-init user `player`, `net0` on `vmbr0` (the uplink) and `net1` on `vmbr1` (the lab side, `172.16.0.1/24`).

Install the mock's dependencies next to the CLI's and start it:

```bash
uv venv && uv pip install --require-hashes -r requirements.txt -r mock/requirements.txt
```

```bash
uv run --no-project python mock/server.py
```

It listens on `https://127.0.0.1:8006` with a self-signed certificate. Add it in the web app as a Proxmox server with IP `127.0.0.1`, node `inferno` and any token in the `USER@REALM!TOKENID=SECRET` form, such as `root@pam!amaterasu=secret`.

### Machines behind the VMs

A running VM behaves like a small machine: you can log in on its console, and a lab's bastion takes real SSH logins. Inside, a simulated shell knows the usual commands (`ls`, `cat`, `cd`, `grep`, pipes, `ip a`, `ping`, `curl`, `nmap`, `ssh` and more; `help` lists them) and the machine's network: its lab NICs reach only the machines on the same `abrN` bridge, so a lab is as isolated as on a real node, and a lab split over two bridges is reached through the machine that sits on both.

| Template | Image | Logins | Listens on | Notes |
| --- | --- | --- | --- | --- |
| `9000` ubuntu-20.04-template | Ubuntu 20.04 | `ubuntu` / `ubuntu` | 22 | |
| `9001` centos-8-template | CentOS 8 | `centos` / `centos` | 22 | |
| `9002` debian-11-template | Debian 11 | `debian` / `debian` | 22, 80 (nginx) | Two NICs, so it joins two lab networks |
| `9003` windows-server-2022-template | Windows Server 2022 | `Administrator` / `Amaterasu!2022` | 22, 135, 445, 3389 | `cmd.exe` |
| `9004` alpine-minimal-template | Alpine 3.20 | `root` / `alpine` | 22, 80 | Fixed at `172.16.0.20`; status page and `/root/flag.txt` hold mock flags |
| `9005` tpl-bastion | Debian 12 | `player` / the copy's password | 22 | Has `nmap`; forwarding off |
| any other VM | Ubuntu 22.04 | `admin` / `admin` | 22 | |

A template's cloud-init user (`ciuser`) logs in with the password the deploy sets, as on a real node. Files written in a session (`echo … > file`, `wget`) stay on that copy until it is destroyed.

**Console.** Every running VM's console shows its login prompt; log in with the table's credentials. Logging out returns to the prompt, and a stopped VM closes its console.

**SSH.** The mock runs an SSH server on this machine's own LAN address, port `2022`, and reports that address as each bastion's uplink lease, so the `ssh` command the app shows works. Every bastion shares the address; the copy's password picks which bastion you land on. From there it works like the real thing:

- `ssh -p 2022 player@<address>` logs in to the bastion;
- `ssh -J player@<address>:2022 root@172.16.0.20` hops to a lab machine, and `ssh` inside the bastion's shell does the same;
- `ssh -p 2022 -L 8080:172.16.0.20:80 player@<address>` serves a lab web server at `http://localhost:8080`.

A VM that stops drops its sessions. The bastion template carries an `ssh-port-2022` tag, which the REST service reads to show `-p 2022` in the app; a real bastion behind a port forward can use the same tag.

| Variable | Default | Description |
| -------- | ------- | ----------- |
| `MOCK_SSH` | `1` | `0` turns the SSH server off; bastions then get made-up addresses |
| `MOCK_SSH_ADDRESS` | this machine's LAN address | The address bastions report and the server listens on |
| `MOCK_SSH_BIND` | `MOCK_SSH_ADDRESS` | Where the server listens, if that differs (e.g. `0.0.0.0` in a container) |
| `MOCK_SSH_PORT` | `2022` | The SSH port |

The server's host key is generated into `mock/ssl/ssh_host_ed25519_key` on first start and kept (it is not committed), so `known_hosts` stays valid between runs.

`mock/selftest.py` deploys a bastion lab through the API the way the REST service does and checks SSH logins, a hop, a forward and a console login; CI runs it.
