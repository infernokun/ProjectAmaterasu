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

`mock/` is a stateful Express server that answers the parts of the Proxmox API the REST service uses:

- Clone, start, stop, shutdown and destroy return a task UPID that finishes after `MOCK_TASK_MS` (default 3000).
- Clones stay locked until their task finishes, and running VMs cannot be destroyed.
- Bridges can be created and deleted, and take effect once network changes are applied.
- `/cluster/nextid` returns a free VM id.

Templates `9000` to `9004` and bridges `vmbr0` and `vmbr1` are seeded from `mock/data`.

```bash
cd mock && pnpm install && pnpm start
```

It listens on `https://127.0.0.1:8006` with a self-signed certificate. Add it in the web app as a Proxmox server with IP `127.0.0.1`, node `inferno` and any API token.
