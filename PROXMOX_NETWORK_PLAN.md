# Proxmox Integration — Business-Logic Evaluation & Network-Config Plan

> Status: **PLAN ONLY** — no code changed. Scope: evaluate the Proxmox deploy
> path, explain why cloned VMs lose networking, and design the
> "select adapter → auto-fill IPs → deploy" feature.

## Context

Deploying a VM lab clones a Proxmox template through the app, but the cloned
VMs come up with **no working network**. The desired fix is a deploy-time
dropdown to choose an available network adapter (bridge) that auto-fills
selectable IPs, so a lab deploys with a real, reachable network config.

The runtime deploy path is **entirely in the Java backend** (`amaterasu-rest`).
The Python `amaterasu-proxmox/main.py` is a standalone experimental CLI helper
and is **not** in the deploy path — it can be ignored for this work (or deleted
later to avoid confusion).

---

## Part 1 — What the existing code actually does (verified)

Deploy flow: `LabActionService.startLab()` →
`ProxmoxService.startAndCloneProxmoxLab()`
(`amaterasu-rest/.../services/alt/ProxmoxService.java:318`).

1. Ensure a management bridge `amaterasu0` (10.254.0.1/16) exists — line 326.
2. Clone each template VM — clone payload is `{name, newid, node, description}`
   with **no network params** (line 348).
3. `modifyProxmoxVMConfig()` (line 557) rewrites the clone's networking.
4. Start VMs, wait for `running`, then persist **status-only** VM snapshots to
   `LabTracker.vms` (line 396).

### Root cause of "VMs lose their network configuration"

This is **by design in `modifyProxmoxVMConfig()`**, not a flaky timeout:

- **No IP is ever set.** The only config pushed is remapped `netN` adapters +
  `agent=1` (lines 625–627). There is no `ipconfig0`, no cloud-init, no static
  addressing anywhere.
- **Adapters are moved to isolated per-team bridges.** Every `bridge=vmbrX` is
  remapped to a fresh `abrN` bridge (lines 580–612) created via
  `ensureNodeBridgeExists`. These `abr` bridges have **no uplink and no DHCP**,
  so a guest that expected DHCP on the original `vmbr` gets nothing, and a guest
  with a hard-coded IP is now on an isolated segment. Either way → no usable
  network. **This is the symptom you hit.**
- **Non-`vmbr` adapters are silently dropped.** The regex only matches
  `bridge=vmbr\d+` (line 580); anything else is never re-added to the update
  map (line 614).

### Secondary defects found

- **`ProxmoxVMConfig.java:23`** — `@JsonAnySetter` keeps only keys starting with
  `net`. Any `ipconfig*`/cloud-init fields Proxmox returns are **silently
  discarded**, so the backend is structurally blind to IP config. Must be fixed
  before any IP feature can work.
- **No IPAM.** Nothing tracks which IPs are in use on a bridge — required for
  "auto-fill available IPs."
- **No network data persisted.** `LabTracker.vms` stores only runtime status
  (`ProxmoxVM` = vmid/name/status/cpu/mem). Chosen bridge + assigned IPs are
  lost, so restart/redeploy can't reapply them.
- **`updateVmNetworkConfig()` (line 264)** doesn't verify the change stuck; no
  post-apply read-back.
- **Weak error handling** — `LabActionService.startLab()` passes results through
  with no validation; partial failures still report VM state.
- **VMID collision risk** — `newVmid = 100 + random(999999900)` (line 342) with
  no existence check; low odds but unbounded.

---

## Part 2 — The feature: adapter dropdown + auto-filled IPs

Three architectural decisions drive the design. Recommended default is marked ★.

### Decision A — How IPs actually get applied inside the guest

| Option | How | Requires | Notes |
|---|---|---|---|
| **A1 ★ Cloud-init `ipconfig0`** | Backend sets `ipconfig0=ip=<ip>/<cidr>,gw=<gw>` on the clone | Templates have a cloud-init drive + cloud-init-capable guest | Cleanest; Proxmox-native; deterministic IPs |
| A2 Bridge + DHCP | App only picks the bridge; a DHCP server on that bridge leases IPs | DHCP infra per bridge | "Auto-fill" becomes lease preview/reservation; least app control |
| A3 App-managed static IPAM | App owns an IP pool per bridge, assigns static, pushes via cloud-init or guest-agent exec | Pool storage + (cloud-init or agent) | Most flexible, most work; superset of A1 |

**Recommendation:** A1 as the mechanism, plus a lightweight IPAM (a slice of A3)
purely to compute "available IPs." If templates are **not** cloud-init enabled,
fall back to A2 for those and surface a clear "no cloud-init → DHCP only"
message. A verification step (below) decides per-template.

### Decision B — Where the UI lives

| Option | Notes |
|---|---|
| **B1 ★ Deploy dialog** | Matches your description — appears on Deploy, next to remote-server select (`RemoteServerSelectData`). Per-deployment control. |
| B2 Lab-creation | Chosen once, stored on `Lab`, reused every deploy. Less flexible. |
| B3 Both | Default at creation, override at deploy. Most work. |

**Recommendation:** B1 now; the model changes leave room for B3 later.

### Decision C — How "available IPs" are computed

Derive the subnet from the selected bridge's `ProxmoxNetwork.cidr`/`address`
(already fetched by `getNodeNetworks`). Enumerate the host range, then subtract
IPs already taken. "Taken" set = union of (a) IPs parsed from existing VM
`ipconfig*` on that node, and (b) IPs recorded in our own IPAM store. Offer the
first N free IPs, pre-filled and editable. **Recommendation:** compute
server-side in a new endpoint so the web app stays thin.

---

## Part 3 — Implementation outline (once decisions confirmed)

### Backend (`amaterasu-rest`)

1. **Fix config capture** — `ProxmoxVMConfig.java`: also retain `ipconfig*`
   (and ideally `ipconfig` map) in the `@JsonAnySetter`, keeping `net*` separate.
2. **New endpoints** in `ProxmoxController.java`:
   - `GET /api/proxmox/bridges?remoteServerId=` → selectable adapters (iface,
     cidr, gateway, free-IP count) built from `getNodeNetworks`.
   - `GET /api/proxmox/available-ips?remoteServerId=&bridge=&count=` → computed
     free IPs (Decision C).
3. **New service logic** in `ProxmoxService`:
   - `listSelectableBridges(remoteServer)` and `computeAvailableIps(...)`.
   - Overload the clone/deploy path to accept a **per-VM network assignment**
     (bridge + IP). Set `ipconfig0` in the clone or immediate post-clone config
     instead of (or alongside) the current `abrN` remap. Gate the `abrN`
     isolation behavior behind an explicit "isolated lab network" flag rather
     than making it the unconditional default.
   - Add read-back verification after `updateVmNetworkConfig`.
4. **Request/persistence model**:
   - Extend the deploy request DTO (the `LabRequest` equivalent consumed by the
     start endpoint) with `networkConfig: [{ vmId, bridge, ip }]`.
   - Add a `networkConfig` field to `LabTracker` (new small embeddable/converter,
     mirroring `ProxmoxVMListConverter`) so bridge+IP survive restart/redeploy.
   - Minimal IPAM: persist assigned IPs (a table keyed by remoteServer+bridge+ip,
     or reuse the LabTracker records as the source of truth).

### Frontend (`amaterasu-web`)

Reuse existing form primitives — no new patterns needed:

- `DropDownQuestion` + `mat-select` (`dialog-question.component.html:55`), async
  options via the `asyncData`/`processAsyncData` path in
  `add-dialog-form.component.ts`, and `dropdownSelectionChanged` to trigger the
  IP auto-fill (same event wiring already used for `remoteServer`).
- Extend `RemoteServerSelectData` (`remote-server.model.ts:97`) to add a
  **Network Adapter** dropdown (visible only for `LabType.VIRTUAL_MACHINE`), and
  a repeated/editable **IP field per VM** populated from the available-ips call.
- Add service methods on `proxmox.service.ts` / `RemoteServerService` for the two
  new endpoints.
- Thread the chosen `networkConfig` into the existing deploy request in
  `lab-deploy.component.ts` (`createLabRequest`/`sendStartRequest`).
- Add `bridge`/`ip` fields to `proxmox-vm.model.ts` (or a new
  `vm-network-config.model.ts`) and show them in `lab-settings` after deploy.

### Sequencing

1. Backend fix: `ProxmoxVMConfig` ipconfig capture + read-back verification
   (unblocks everything, small).
2. Bridges + available-IPs endpoints and service logic.
3. Deploy path accepts and applies per-VM bridge+IP; persist on `LabTracker`.
4. Frontend dropdown + IP auto-fill + wiring.
5. Decide fate of the `abrN` auto-isolation (flag vs remove).

---

## Verification

- **Unit:** `proxmox.service.spec.ts` exists — extend for the new service calls.
  Add backend tests for IP enumeration/exclusion and `ipconfig0` payload build.
- **Mock server:** `amaterasu-proxmox/mock/server.js` already stubs
  `GET/POST/PUT /nodes/:node/network`, clone, and config — extend it to accept
  `ipconfig0` and return it in the VM config so the full flow can be exercised
  without a real Proxmox.
- **End-to-end (real Proxmox):** deploy a lab, confirm each cloned VM shows the
  chosen `ipconfig0` in Proxmox, boots with that IP, and is reachable on the
  selected bridge; restart the lab and confirm bridge+IP reapply from
  `LabTracker`.

## Open questions to confirm before coding

1. IP mechanism — A1 cloud-init (★), A2 DHCP, or A3 static IPAM?
2. Are the VM templates cloud-init enabled? (decides A1 feasibility per template)
3. UI placement — deploy dialog (★), lab-creation, or both?
4. Keep the per-team isolated `abrN` bridge behavior (behind a flag) or drop it?
