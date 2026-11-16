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

Dependencies are pinned in `requirements.in` and hash-locked in `requirements.txt`. Run `npm run deps:lock` from the repo root after changing the `.in` file.

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

With the same environment as the CLI:

```bash
uv run --no-project python mock/server.py
```

It listens on `https://127.0.0.1:8006` with a self-signed certificate. Add it in the web app as a Proxmox server with IP `127.0.0.1`, node `inferno` and any token in the `USER@REALM!TOKENID=SECRET` form, such as `root@pam!amaterasu=secret`.
