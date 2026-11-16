"""Stateful Proxmox VE API mock: long running calls return a task UPID that finishes after
MOCK_TASK_MS, clones stay locked until then, and running VMs cannot be destroyed."""

import asyncio
import copy
import functools
import json
import os
import random
import re
import ssl
import time
from pathlib import Path

from aiohttp import web
from vnc import console_handler, issue_ticket

HERE = Path(__file__).parent
HOST = os.getenv("MOCK_HOST", "127.0.0.1")
PORT = int(os.getenv("MOCK_PORT", "8006"))
TASK_MS = int(os.getenv("MOCK_TASK_MS", "3000"))
# How long after a start the guest agent of a clone takes to answer
AGENT_MS = int(os.getenv("MOCK_AGENT_MS", "8000"))
API = "/api2/json"
# Compact, like Express
dumps = functools.partial(json.dumps, separators=(",", ":"), ensure_ascii=False)


def number(text: str):
    """Parse JSON numbers as JavaScript does, so 10.0 is sent back as 10."""
    value = float(text)
    return int(value) if value.is_integer() else value


def load(name: str):
    return json.loads((HERE / "data" / name).read_text(), parse_float=number)


def now_ms() -> int:
    return int(time.time() * 1000)


VERSION = load("version.json")
NODES = load("nodes.json")
NODE_DETAILS = load("nodeDetails.json")
STORAGE = load("storage.json")
TICKETS = load("tickets.json")
# Answers from a PVE 9.2.2 node called proxmox2
NODE_STATUS = load("nodeStatus.json")
PERMISSIONS = load("permissions.json")
STARTED = now_ms()

vms: dict[int, dict] = {}
configs: dict[int, dict] = {}
tasks: dict[str, dict] = {}
networks: list[dict] = load("networkInterfaces.json")["data"]
pending_networks: list[dict] | None = None

MAC = re.compile(r"^[0-9a-f]{2}(:[0-9a-f]{2}){5}$", re.I)
MAC_VALUE = re.compile(r"=([0-9A-Fa-f:]{17})")
NIC_KEY = re.compile(r"^net\d+$")
BRIDGE = re.compile(r"bridge=([^,]+)")


def random_mac() -> str:
    return "BC:24:11:" + ":".join(f"{random.randrange(256):02X}" for _ in range(3))


def with_mac(nic) -> str:
    """Like PVE, a NIC written without a MAC ("virtio,bridge=abr0") gets a fresh one."""
    parts = str(nic).split(",")
    if any(p.startswith("macaddr=") or MAC.match(p.split("=")[1] if "=" in p else "") for p in parts):
        return ",".join(parts)
    if parts[0].startswith("model="):
        model = parts.pop(0)[len("model="):]
    elif "=" in parts[0]:
        model = None
    else:
        model = parts.pop(0)
    return ",".join([f"{model or 'virtio'}={random_mac()}", *parts])


# Insertion ordered, like a JavaScript Set
known_nodes = dict.fromkeys(n["node"] for n in NODES["data"])
for vm in load("vms.json")["data"]:
    known_nodes[vm["node"]] = None
    vms[vm["vmid"]] = {**vm, "status": "stopped" if vm.get("template") else vm["status"]}
    config = {"name": vm["name"], "agent": "0", "net0": f"virtio={random_mac()},bridge=vmbr0,firewall=1"}
    if vm["vmid"] in (9000, 9002):
        config["net1"] = f"virtio={random_mac()},bridge=vmbr1,firewall=1"
    if vm["vmid"] == 9001:
        config["net0"] = f"virtio={random_mac()},bridge=vmbr1,firewall=1"
    if vm["vmid"] == 9004:
        # A target with the guest agent, at a fixed lab address
        config.update({"agent": "1", "tags": "amaterasu", "ipconfig0": "ip=172.16.0.20/24"})
        config["net0"] = f"virtio={random_mac()},bridge=vmbr1"
    configs[vm["vmid"]] = config

# A bastion like tpl-bastion on proxmox2: net0 is the uplink, net1 joins the lab
vms[9005] = {"vmid": 9005, "name": "tpl-bastion", "status": "stopped", "uptime": 0, "mem": 0, "maxmem": 536870912,
             "cpu": 0, "maxcpu": 1, "node": "inferno", "template": 1}
configs[9005] = {
    "name": "tpl-bastion", "agent": "1", "tags": "amaterasu;bastion", "ciuser": "player",
    "net0": f"virtio={random_mac()},bridge=vmbr0", "net1": f"virtio={random_mac()},bridge=vmbr1",
    "ipconfig0": "ip=dhcp", "ipconfig1": "ip=172.16.0.1/24",
}


class ApiError(Exception):
    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status
        self.message = message


@web.middleware
async def api_errors(request: web.Request, handler):
    try:
        return await handler(request)
    except ApiError as e:
        return json_response({"data": None, "message": e.message, "errors": {"message": e.message}},
                                 status=e.status)


def json_response(data, status: int = 200) -> web.Response:
    return web.json_response(data, status=status, dumps=dumps)


def ok(data=None) -> web.Response:
    return json_response({"data": data})


async def body(request: web.Request) -> dict:
    """The request body as JSON or a form, like Express's json and urlencoded parsers."""
    if not request.body_exists:
        return {}
    if request.content_type == "application/json":
        try:
            parsed = await request.json()
        except ValueError:
            raise ApiError(400, "invalid JSON body") from None
        return parsed if isinstance(parsed, dict) else {}
    if request.content_type == "application/x-www-form-urlencoded":
        return dict(await request.post())
    return {}


def start_task(node: str, kind: str, vmid, action) -> str:
    upid = f"UPID:{node}:{now_ms():x}:{random.randrange(1_000_000)}:{kind}:{vmid}:root@pam:"
    tasks[upid] = {"status": "running", "exitstatus": None, "type": kind, "id": str(vmid)}

    def finish():
        exitstatus = "OK"
        try:
            action()
        except Exception as e:  # noqa: BLE001 - a failing action is the task's exit status
            exitstatus = str(e)
        tasks[upid] = {"status": "stopped", "exitstatus": exitstatus, "type": kind, "id": str(vmid)}

    asyncio.get_running_loop().call_later(TASK_MS / 1000, finish)
    return upid


def get_vm(request: web.Request) -> dict:
    node = request.match_info["node"]
    vmid_text = request.match_info["vmid"]
    vm = vms.get(int(vmid_text)) if vmid_text.isdigit() else None
    if not vm or vm["node"] != node:
        raise ApiError(500, f"Configuration file 'nodes/{node}/qemu-server/{vmid_text}.conf' does not exist")
    return vm


def require_unlocked(vm: dict):
    if vm.get("lock"):
        raise ApiError(500, f"VM is locked ({vm['lock']})")


routes = web.RouteTableDef()


@routes.get(API + "/version")
async def version(request):
    return json_response(VERSION)


@routes.post(API + "/access/ticket")
async def access_ticket(request):
    return json_response(TICKETS)


@routes.get(API + "/nodes")
async def list_nodes(request):
    return json_response(NODES)


@routes.get(API + "/storage")
async def list_storage(request):
    return json_response(STORAGE)


@routes.get(API + "/nodes/{node}")
async def node_details(request):
    response = copy.deepcopy(NODE_DETAILS)
    response["data"]["node"] = request.match_info["node"]
    return json_response(response)


@routes.get(API + "/nodes/{node}/status")
async def node_status(request):
    response = copy.deepcopy(NODE_STATUS)
    response["data"]["uptime"] += (now_ms() - STARTED) // 1000
    return json_response(response)


@routes.get(API + "/access/permissions")
async def access_permissions(request):
    """The token's privileges per path; the recorded node's entry stands for every node."""
    node_privileges = PERMISSIONS["data"]["/nodes/proxmox2"]
    everything = {p: privs for p, privs in PERMISSIONS["data"].items() if p != "/nodes/proxmox2"}
    for node in known_nodes:
        everything[f"/nodes/{node}"] = node_privileges
    path = request.query.get("path")
    if not path:
        return ok(everything)
    return ok({path: everything.get(path) or (node_privileges if path.startswith("/nodes/") else {})})


@routes.get(API + "/cluster/nextid")
async def next_id(request):
    vmid = 100
    while vmid in vms:
        vmid += 1
    return ok(str(vmid))


@routes.get(API + "/nodes/{node}/tasks/{upid}/status")
async def task_status(request):
    upid = request.match_info["upid"]
    task = tasks.get(upid)
    if not task:
        raise ApiError(500, "no such task")
    return ok({"upid": upid, "node": request.match_info["node"], **task})


@routes.get(API + "/nodes/{node}/qemu")
async def list_vms(request):
    return ok([vm for vm in vms.values() if vm["node"] == request.match_info["node"]])


@routes.get(API + "/nodes/{node}/qemu/{vmid}/status/current")
async def vm_status(request):
    return ok(get_vm(request))


@routes.get(API + "/nodes/{node}/qemu/{vmid}/config")
async def vm_config(request):
    vm = get_vm(request)
    return ok({**configs[vm["vmid"]], **({"lock": vm["lock"]} if vm.get("lock") else {})})


@routes.post(API + "/nodes/{node}/qemu/{vmid}/config")
@routes.put(API + "/nodes/{node}/qemu/{vmid}/config")
async def update_config(request):
    vm = get_vm(request)
    require_unlocked(vm)
    config = configs[vm["vmid"]]
    changes = await body(request)
    to_delete = changes.pop("delete", None)
    changes.pop("node", None)
    for key, value in changes.items():
        if NIC_KEY.match(key):
            bridge = BRIDGE.search(str(value))
            if bridge and not any(n["iface"] == bridge[1] for n in networks):
                raise ApiError(400, f"bridge '{bridge[1]}' does not exist")
            config[key] = with_mac(value)
            continue
        # PVE keeps only a hash and shows it masked
        config[key] = "**********" if key == "cipassword" else js_string(value)
    if to_delete:
        for key in str(to_delete).split(","):
            config.pop(key.strip(), None)
    return ok()


def js_string(value) -> str:
    """String(value) as JavaScript writes it, for values sent as JSON."""
    if isinstance(value, bool):
        return "true" if value else "false"
    if value is None:
        return "null"
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value)


@routes.post(API + "/nodes/{node}/qemu/{vmid}/clone")
async def clone_vm(request):
    source = get_vm(request)
    require_unlocked(source)
    params = await body(request)
    try:
        newid = int(params.get("newid") or 0)
    except (TypeError, ValueError):
        newid = 0
    if not newid or newid in vms:
        raise ApiError(500, f"unable to create VM {params.get('newid')}: config file already exists")

    clone = {**source, "vmid": newid, "name": params.get("name") or f"{source['name']}-clone",
             "description": params.get("description"), "template": 0, "status": "stopped", "uptime": 0,
             "lock": "clone"}
    if clone["description"] is None:
        # JSON.stringify leaves out a description that wasn't sent
        del clone["description"]
    vms[newid] = clone
    config = {**configs[source["vmid"]], "name": clone["name"]}
    for key in config:
        if NIC_KEY.match(key):
            config[key] = MAC_VALUE.sub(f"={random_mac()}", config[key], count=1)
    configs[newid] = config

    return ok(start_task(request.match_info["node"], "qmclone", source["vmid"], lambda: clone.pop("lock", None)))


def status_action(kind: str, check, final_status: str):
    async def handler(request):
        vm = get_vm(request)
        require_unlocked(vm)
        problem = check(vm)
        if problem:
            raise ApiError(500, problem)

        def finish():
            running = final_status == "running"
            vm["status"] = final_status
            vm["uptime"] = 1 if running else 0
            vm["startedAt"] = now_ms() if running else None

        return ok(start_task(request.match_info["node"], kind, vm["vmid"], finish))

    return handler


routes.post(API + "/nodes/{node}/qemu/{vmid}/status/start")(status_action(
    "qmstart", lambda vm: "you can't start a vm if it's a template" if vm.get("template") else None, "running"))
routes.post(API + "/nodes/{node}/qemu/{vmid}/status/stop")(status_action("qmstop", lambda vm: None, "stopped"))
routes.post(API + "/nodes/{node}/qemu/{vmid}/status/shutdown")(status_action(
    "qmshutdown", lambda vm: f"VM {vm['vmid']} not running" if vm["status"] != "running" else None, "stopped"))


def agent_enabled(config: dict) -> bool:
    return bool(re.search(r"^(1|on|yes|true)$|(^|,)enabled=(1|on|yes|true)(,|$)", str(config.get("agent") or "0")))


@routes.get(API + "/nodes/{node}/qemu/{vmid}/agent/network-get-interfaces")
async def agent_interfaces(request):
    """Clones answer AGENT_MS after they start. Uplink NICs (not on an abrN lab bridge) get a LAN address
    by DHCP; lab NICs get their static ipconfig, else one from 172.16.0.0/24."""
    vm = get_vm(request)
    config = configs[vm["vmid"]]
    if not agent_enabled(config):
        raise ApiError(500, "No QEMU guest agent configured")
    if vm["status"] != "running":
        raise ApiError(500, f"VM {vm['vmid']} is not running")
    if not vm.get("startedAt") or now_ms() - vm["startedAt"] < AGENT_MS:
        raise ApiError(500, "QEMU guest agent is not running")
    result = [{"name": "lo", "hardware-address": "00:00:00:00:00:00",
               "ip-addresses": [{"ip-address": "127.0.0.1", "ip-address-type": "ipv4", "prefix": 8}]}]
    for index, key in enumerate(sorted(k for k in config if NIC_KEY.match(k))):
        n = key[3:]
        mac = MAC_VALUE.search(config[key])
        bridge = BRIDGE.search(config[key])
        fixed = re.search(r"ip=([\d.]+)/(\d+)", config.get(f"ipconfig{n}") or "")
        lan = not re.match(r"^abr\d+$", bridge[1] if bridge else "")
        if fixed:
            ip = fixed[1]
        elif lan:
            ip = f"10.0.0.{20 + vm['vmid'] % 200}"
        else:
            ip = f"172.16.0.{100 + vm['vmid'] % 100}"
        result.append({"name": f"eth{index}", "hardware-address": mac[1].lower() if mac else "",
                       "ip-addresses": [{"ip-address": ip, "ip-address-type": "ipv4",
                                         "prefix": int(fixed[2]) if fixed else 24}]})
    return ok({"result": result})


@routes.delete(API + "/nodes/{node}/qemu/{vmid}")
async def destroy_vm(request):
    vm = get_vm(request)
    require_unlocked(vm)
    if vm["status"] == "running":
        raise ApiError(500, f"VM {vm['vmid']} is running - destroy failed")
    vm["lock"] = "destroyed"

    def finish():
        vms.pop(vm["vmid"], None)
        configs.pop(vm["vmid"], None)

    return ok(start_task(request.match_info["node"], "qmdestroy", vm["vmid"], finish))


@routes.post(API + "/nodes/{node}/qemu/{vmid}/vncproxy")
async def vnc_proxy(request):
    vm = get_vm(request)
    require_unlocked(vm)
    if vm.get("template"):
        raise ApiError(500, "you can't open a console on a template")
    if vm["status"] != "running":
        raise ApiError(500, f"VM {vm['vmid']} not running")
    return ok(issue_ticket(request.match_info["node"], vm))


def stanza(n: dict) -> list[str]:
    lines = [f"auto {n['iface']}"] if n.get("autostart") else []
    lines.append(f"iface {n['iface']} inet {n.get('method') or 'manual'}")
    if n.get("type") == "bridge":
        lines += [f"\tbridge-ports {n.get('bridge_ports') or 'none'}", "\tbridge-stp off", "\tbridge-fd 0"]
    if n.get("comments"):
        lines.append(f"#{str(n['comments']).strip()}")
    return lines


def pending_changes() -> str | None:
    """Like PVE, "changes" is a diff of interfaces against interfaces.new, sent only while edits are pending."""
    if pending_networks is None:
        return None
    before = {n["iface"] for n in networks}
    after = {n["iface"] for n in pending_networks}
    hunks = []
    for n in networks:
        if n["iface"] not in after:
            hunks += ["@@ -1,1 +1,0 @@", "-", *(f"-{line}" for line in stanza(n))]
    for n in pending_networks:
        if n["iface"] not in before:
            hunks += ["@@ -1,0 +1,1 @@", "+", *(f"+{line}" for line in stanza(n))]
    if not hunks:
        return None
    return "\n".join(["--- /etc/network/interfaces", "+++ /etc/network/interfaces.new", *hunks]) + "\n"


def editable_networks() -> list[dict]:
    global pending_networks
    if pending_networks is None:
        pending_networks = copy.deepcopy(networks)
    return pending_networks


@routes.get(API + "/nodes/{node}/network")
async def list_networks(request):
    changes = pending_changes()
    return json_response({**({"changes": changes} if changes else {}),
                              "data": networks if pending_networks is None else pending_networks})


@routes.post(API + "/nodes/{node}/network")
async def create_network(request):
    params = await body(request)
    iface = params.get("iface")
    target = editable_networks()
    if any(n["iface"] == iface for n in target):
        raise ApiError(400, f"interface '{iface}' already exists")
    entry = {"iface": iface, "type": params.get("type"), "active": 0,
             "autostart": int(params.get("autostart") or 0), "comments": params.get("comments"), "families": []}
    # JSON.stringify leaves out what wasn't sent
    target.append({k: v for k, v in entry.items() if v is not None})
    return ok()


@routes.delete(API + "/nodes/{node}/network/{iface}")
async def delete_network(request):
    global pending_networks
    iface = request.match_info["iface"]
    target = editable_networks()
    if not any(n["iface"] == iface for n in target):
        raise ApiError(400, f"interface '{iface}' does not exist")
    pending_networks = [n for n in target if n["iface"] != iface]
    return ok()


@routes.put(API + "/nodes/{node}/network")
async def apply_networks(request):
    global networks, pending_networks
    if pending_networks is not None:
        networks = [{**n, "active": 1} for n in pending_networks]
        pending_networks = None
    return ok(start_task(request.match_info["node"], "srvreload", "networking", lambda: None))


@routes.get("/")
async def root(request):
    return web.Response(text="Proxmox VE API Mock Server", content_type="text/html")


async def not_implemented(request):
    print(f"Unhandled request: {request.method} {request.path}")
    return web.Response(status=501, text="API endpoint not implemented in mock", content_type="text/html")


def make_app() -> web.Application:
    app = web.Application(middlewares=[api_errors])
    app.router.add_get(API + "/nodes/{node}/qemu/{vmid}/vncwebsocket", console_handler(vms))
    app.add_routes(routes)
    app.router.add_route("*", "/{tail:.*}", not_implemented)
    return app


def main():
    context = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH)
    context.load_cert_chain(HERE / "ssl" / "cert.pem", HERE / "ssl" / "key.pem")
    print(f"Proxmox API Mock HTTPS Server running at https://{HOST}:{PORT}/")
    web.run_app(make_app(), host=HOST, port=PORT, ssl_context=context, print=None, access_log=None)


if __name__ == "__main__":
    main()
