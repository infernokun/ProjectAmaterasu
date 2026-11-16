"""Stateful Proxmox VE API mock that answers like the PVE 9.2 node the REST service is built against.

Long running calls return a task UPID that finishes after MOCK_TASK_MS, clones stay locked until then,
running VMs cannot be destroyed, and a VM only starts once the bridges its NICs use are applied."""

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

import sshd
from aiohttp import web
from machines import TEMPLATE_IMAGES, World
from vnc import console_handler, issue_ticket

HERE = Path(__file__).parent
HOST = os.getenv("MOCK_HOST", "127.0.0.1")
PORT = int(os.getenv("MOCK_PORT", "8006"))
TASK_MS = int(os.getenv("MOCK_TASK_MS", "3000"))
# How long after a start the guest agent of a clone takes to answer
AGENT_MS = int(os.getenv("MOCK_AGENT_MS", "8000"))
API = "/api2/json"
dumps = functools.partial(json.dumps, separators=(",", ":"), ensure_ascii=False)


def number(text: str):
    """Parse JSON numbers as PVE writes them, so 10.0 is sent back as 10."""
    value = float(text)
    return int(value) if value.is_integer() else value


def load(name: str):
    return json.loads((HERE / "data" / name).read_text(), parse_float=number)


def now_ms() -> int:
    return int(time.time() * 1000)


VERSION = load("version.json")
NODES = load("nodes.json")
STORAGE = load("storage.json")
TICKETS = load("tickets.json")
# Answers from a PVE 9.2.2 node called proxmox2
NODE_STATUS = load("nodeStatus.json")
PERMISSIONS = load("permissions.json")
STARTED = now_ms()

vms: dict[int, dict] = {}
configs: dict[int, dict] = {}
started_at: dict[int, int] = {}
tasks: dict[str, dict] = {}
networks: list[dict] = load("networkInterfaces.json")["data"]
pending_networks: list[dict] | None = None
# What runs inside the VMs: the cipassword as set (the API only shows it masked) and each disk's image
secrets: dict[int, str] = {}
images: dict[int, str] = {}
world = World(vms, configs, started_at, secrets, images)
# Insertion ordered: the nodes of the cluster, as GET /nodes lists them
known_nodes = dict.fromkeys(n["node"] for n in NODES["data"])

MAC = re.compile(r"^[0-9a-f]{2}(:[0-9a-f]{2}){5}$", re.I)
MAC_VALUE = re.compile(r"=([0-9A-Fa-f:]{17})")
NIC_KEY = re.compile(r"^net\d+$")
BRIDGE = re.compile(r"bridge=([^,]+)")
# USER@REALM!TOKENID=SECRET, after "PVEAPIToken=" or "PVEAPIToken "
TOKEN = re.compile(r"^PVEAPIToken[= ]([^@\s=]+@[^!\s=]+![A-Za-z][\w.-]*)=(\S+)$")
GiB = 1024 ** 3


def random_mac() -> str:
    return "BC:24:11:" + ":".join(f"{random.randrange(256):02X}" for _ in range(3))


def random_hex(length: int) -> str:
    return "".join(random.choice("0123456789abcdef") for _ in range(length))


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


def template_config(vm: dict, **extra) -> dict:
    """A VM config with the keys a PVE node keeps, besides the ones the REST service reads."""
    vmid = vm["vmid"]
    disk = "base" if vm["template"] else "vm"
    config = {
        "boot": "order=scsi0", "cores": vm["cpus"], "memory": str(vm["maxmem"] // (1024 * 1024)),
        "meta": "creation-qemu=10.0.2,ctime=1793931200", "name": vm["name"], "ostype": "l26",
        "scsi0": f"local-lvm:{disk}-{vmid}-disk-0,size=32G", "scsihw": "virtio-scsi-pci",
        "smbios1": f"uuid={random_hex(8)}-{random_hex(4)}-4{random_hex(3)}-a{random_hex(3)}-{random_hex(12)}",
        "vmgenid": f"{random_hex(8)}-{random_hex(4)}-4{random_hex(3)}-9{random_hex(3)}-{random_hex(12)}",
        "agent": "0",
    }
    config.update(extra)
    if vm["template"]:
        config["template"] = 1
    return config


for vm in load("vms.json")["data"]:
    vmid = vm["vmid"]
    vms[vmid] = dict(vm)
    if vm["status"] == "running":
        started_at[vmid] = now_ms() - vm["uptime"] * 1000
    config = template_config(vm, net0=f"virtio={random_mac()},bridge=vmbr0,firewall=1")
    if vmid in (9000, 9002):
        config["net1"] = f"virtio={random_mac()},bridge=vmbr1,firewall=1"
    if vmid == 9001:
        config["net0"] = f"virtio={random_mac()},bridge=vmbr1,firewall=1"
    if vmid == 9004:
        # A target with the guest agent, at a fixed lab address
        config.update({"agent": "1", "tags": "amaterasu", "ipconfig0": "ip=172.16.0.20/24",
                       "ide2": "local-lvm:vm-9004-cloudinit,media=cdrom"})
        config["net0"] = f"virtio={random_mac()},bridge=vmbr1"
    configs[vmid] = config
    images[vmid] = TEMPLATE_IMAGES.get(vmid, "server")

# A bastion like tpl-bastion on proxmox2: net0 is the uplink, net1 joins the lab
vms[9005] = {"vmid": 9005, "name": "tpl-bastion", "status": "stopped", "uptime": 0, "cpu": 0, "cpus": 1, "mem": 0,
             "maxmem": 536870912, "node": "inferno", "template": 1}
configs[9005] = template_config(
    vms[9005], agent="1", tags="amaterasu;bastion", ciuser="player", ide2="local-lvm:vm-9005-cloudinit,media=cdrom",
    net0=f"virtio={random_mac()},bridge=vmbr0", net1=f"virtio={random_mac()},bridge=vmbr1",
    ipconfig0="ip=dhcp", ipconfig1="ip=172.16.0.1/24")
images[9005] = TEMPLATE_IMAGES[9005]
for vm in vms.values():
    known_nodes[vm["node"]] = None


class ApiError(Exception):
    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status
        self.message = message


def json_response(data, status: int = 200, reason: str | None = None) -> web.Response:
    return web.json_response(data, status=status, reason=reason, dumps=dumps)


def ok(data=None) -> web.Response:
    return json_response({"data": data})


def error(status: int, message: str) -> web.Response:
    # PVE's messages end with a newline
    return json_response({"data": None, "message": message.rstrip("\n") + "\n"}, status=status)


def needs_token(request: web.Request) -> bool:
    return request.path.startswith(API + "/") and not (request.method == "POST" and request.path == API + "/access/ticket")


@web.middleware
async def pve(request: web.Request, handler):
    """API token check, unknown nodes and PVE's error bodies, for every route."""
    if needs_token(request):
        token = TOKEN.match(request.headers.get("Authorization", ""))
        if not token:
            return json_response({"data": None}, status=401, reason="authentication failure")
        request["token_user"] = token[1]
    node = request.match_info.get("node")
    if node is not None and node not in known_nodes:
        return error(500, f"hostname lookup '{node}' failed - failed to get address info for: {node}: "
                          "Name or service not known")
    try:
        return await handler(request)
    except ApiError as e:
        return error(e.status, e.message)


async def body(request: web.Request) -> dict:
    """The request body as JSON or a form; PVE takes either."""
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


def start_task(request: web.Request, kind: str, task_id, action) -> str:
    """A task as PVE runs it: the UPID now, the outcome after TASK_MS. A raised error is the exit status."""
    node = request.match_info["node"]
    user = request.get("token_user", "root@pam")
    pid = random.randrange(1000, 4_000_000)
    pstart = random.randrange(1_000_000, 99_999_999)
    starttime = int(time.time())
    upid = f"UPID:{node}:{pid:08X}:{pstart:08X}:{starttime:08X}:{kind}:{task_id}:{user}:"
    tasks[upid] = {"upid": upid, "node": node, "pid": pid, "pstart": pstart, "starttime": starttime,
                   "type": kind, "id": str(task_id), "user": user, "status": "running"}

    def finish():
        exitstatus = "OK"
        try:
            action()
        except Exception as e:  # noqa: BLE001 - a failing action is the task's exit status
            exitstatus = str(e)
        tasks[upid] = {**tasks[upid], "status": "stopped", "exitstatus": exitstatus}

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


def agent_enabled(config: dict) -> bool:
    return bool(re.search(r"^(1|on|yes|true)$|(^|,)enabled=(1|on|yes|true)(,|$)", str(config.get("agent") or "0")))


def vm_view(vm: dict) -> dict:
    """A VM as /qemu lists it: uptime counts from the start, and a stopped VM uses no CPU or memory."""
    view = dict(vm)
    if vm["status"] == "running" and vm["vmid"] in started_at:
        view["uptime"] = (now_ms() - started_at[vm["vmid"]]) // 1000
    elif vm["status"] != "running":
        view.update(uptime=0, cpu=0, mem=0)
    return view


def nic_bridges(config: dict) -> list[str]:
    return [b[1] for key, value in sorted(config.items()) if NIC_KEY.match(key) and (b := BRIDGE.search(str(value)))]


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
async def node_index(request):
    """Like PVE, a node's own path is an index of what is under it."""
    names = ["agent", "apt", "capabilities", "ceph", "certificates", "config", "disks", "dns", "execute", "firewall",
             "hardware", "hosts", "journal", "lxc", "netstat", "network", "qemu", "query-url-metadata", "replication",
             "report", "rrd", "rrddata", "scan", "sdn", "services", "spiceshell", "startall", "status", "stopall",
             "storage", "subscription", "suspendall", "syslog", "tasks", "termproxy", "time", "version", "vncshell",
             "vzdump", "wakeonlan"]
    return ok([{"name": name} for name in names])


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
    asked = request.query.get("vmid")
    if asked:
        if asked.isdigit() and int(asked) in vms:
            raise ApiError(400, f"VM {asked} already exists")
        return ok(asked)
    vmid = 100
    while vmid in vms:
        vmid += 1
    return ok(str(vmid))


@routes.get(API + "/nodes/{node}/tasks/{upid}/status")
async def task_status(request):
    task = tasks.get(request.match_info["upid"])
    if not task:
        raise ApiError(500, "no such task")
    return ok(task)


@routes.get(API + "/nodes/{node}/qemu")
async def list_vms(request):
    return ok([vm_view(vm) for vm in vms.values() if vm["node"] == request.match_info["node"]])


@routes.get(API + "/nodes/{node}/qemu/{vmid}/status/current")
async def vm_status(request):
    vm = get_vm(request)
    view = vm_view(vm)
    view.update(qmpstatus=vm["status"], agent=1 if agent_enabled(configs[vm["vmid"]]) else 0, ha={"managed": 0})
    if vm["status"] == "running":
        view["pid"] = 100_000 + vm["vmid"]
    return ok(view)


@routes.get(API + "/nodes/{node}/qemu/{vmid}/config")
async def vm_config(request):
    vm = get_vm(request)
    config = configs[vm["vmid"]]
    return ok({**config, "digest": config.get("digest") or random_hex(40),
               **({"lock": vm["lock"]} if vm.get("lock") else {})})


async def apply_config(request) -> dict:
    vm = get_vm(request)
    require_unlocked(vm)
    config = configs[vm["vmid"]]
    changes = await body(request)
    to_delete = changes.pop("delete", None)
    for key in ("node", "vmid", "digest"):
        changes.pop(key, None)
    for key, value in changes.items():
        if NIC_KEY.match(key):
            bridge = BRIDGE.search(str(value))
            if bridge and not any(n["iface"] == bridge[1] for n in networks):
                raise ApiError(400, f"bridge '{bridge[1]}' does not exist")
            config[key] = with_mac(value)
            continue
        # PVE keeps only a hash and shows it masked; the machine itself takes the password
        if key == "cipassword":
            secrets[vm["vmid"]] = str(value)
        config[key] = "**********" if key == "cipassword" else js_string(value)
    if to_delete:
        for key in str(to_delete).split(","):
            config.pop(key.strip(), None)
    config["digest"] = random_hex(40)
    return vm


@routes.put(API + "/nodes/{node}/qemu/{vmid}/config")
async def set_config(request):
    """PUT applies the change before it answers."""
    await apply_config(request)
    return ok()


@routes.post(API + "/nodes/{node}/qemu/{vmid}/config")
async def set_config_async(request):
    """POST is PVE's asynchronous variant: it answers with a task."""
    vm = await apply_config(request)
    return ok(start_task(request, "qmconfig", vm["vmid"], lambda: None))


def js_string(value) -> str:
    """A JSON value as PVE stores it in the config."""
    if isinstance(value, bool):
        return "1" if value else "0"
    if value is None:
        return ""
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
    if newid < 100:
        raise ApiError(400, f"invalid VM id {params.get('newid')}")
    if newid in vms:
        raise ApiError(500, f"unable to create VM {newid}: config file already exists")

    name = params.get("name") or f"Copy-of-VM-{source['name']}"
    clone = {**source, "vmid": newid, "name": name, "template": 0, "status": "stopped", "uptime": 0, "cpu": 0,
             "mem": 0, "lock": "clone"}
    vms[newid] = clone
    config = {k: v for k, v in configs[source["vmid"]].items() if k not in ("template", "digest")}
    config["name"] = name
    if params.get("description") is not None:
        config["description"] = str(params["description"])
    for key in config:
        if NIC_KEY.match(key):
            config[key] = MAC_VALUE.sub(f"={random_mac()}", config[key], count=1)
        elif isinstance(config[key], str):
            config[key] = config[key].replace(f"base-{source['vmid']}-", f"vm-{newid}-").replace(
                f"vm-{source['vmid']}-", f"vm-{newid}-")
    configs[newid] = config
    # The clone's disk is a copy of the template's, cloud-init settings included
    images[newid] = images.get(source["vmid"], "server")
    if source["vmid"] in secrets:
        secrets[newid] = secrets[source["vmid"]]

    return ok(start_task(request, "qmclone", source["vmid"], lambda: clone.pop("lock", None)))


def start(vm: dict):
    # A NIC on a bridge that isn't applied yet fails the start, as on a node
    for bridge in nic_bridges(configs[vm["vmid"]]):
        if not any(n["iface"] == bridge and n.get("active") for n in networks):
            raise RuntimeError(f"bridge '{bridge}' does not exist")
    vm.update(status="running", cpu=round(random.uniform(0.01, 0.08), 4), mem=int(vm["maxmem"] * 0.35))
    started_at[vm["vmid"]] = now_ms()


def stop(vm: dict):
    vm.update(status="stopped", uptime=0, cpu=0, mem=0)
    started_at.pop(vm["vmid"], None)


def status_action(kind: str, check, action):
    async def handler(request):
        vm = get_vm(request)
        require_unlocked(vm)
        problem = check(vm)
        if problem:
            raise ApiError(500, problem)
        return ok(start_task(request, kind, vm["vmid"], lambda: action(vm)))

    return handler


routes.post(API + "/nodes/{node}/qemu/{vmid}/status/start")(status_action(
    "qmstart", lambda vm: "you can't start a vm if it's a template" if vm.get("template") else None, start))
routes.post(API + "/nodes/{node}/qemu/{vmid}/status/stop")(status_action("qmstop", lambda vm: None, stop))
routes.post(API + "/nodes/{node}/qemu/{vmid}/status/shutdown")(status_action(
    "qmshutdown", lambda vm: f"VM {vm['vmid']} not running" if vm["status"] != "running" else None, stop))


@routes.get(API + "/nodes/{node}/qemu/{vmid}/agent/network-get-interfaces")
async def agent_interfaces(request):
    """The guest agent answers AGENT_MS after a start. Uplink NICs (not on an abrN lab bridge) get a LAN address
    by DHCP; lab NICs get their static ipconfig, else one from 172.16.0.0/24."""
    vm = get_vm(request)
    config = configs[vm["vmid"]]
    if not agent_enabled(config):
        raise ApiError(500, "No QEMU guest agent configured")
    if vm["status"] != "running":
        raise ApiError(500, f"VM {vm['vmid']} is not running")
    if vm["vmid"] not in started_at or now_ms() - started_at[vm["vmid"]] < AGENT_MS:
        raise ApiError(500, "QEMU guest agent is not running")
    result = [{"hardware-address": "00:00:00:00:00:00", "name": "lo",
               "ip-addresses": [{"ip-address": "127.0.0.1", "ip-address-type": "ipv4", "prefix": 8},
                                {"ip-address": "::1", "ip-address-type": "ipv6", "prefix": 128}]}]
    machine = world.machine(vm["vmid"])
    for iface in machine.interfaces if machine else []:
        addresses = [{"ip-address": iface.ip, "ip-address-type": "ipv4", "prefix": iface.prefix}]
        if iface.mac:
            # The link-local address PVE guests also report, from the MAC
            octets = iface.mac.split(":")
            eui = f"{int(octets[0], 16) ^ 2:02x}{octets[1]}:{octets[2]}ff:fe{octets[3]}:{octets[4]}{octets[5]}"
            addresses.append({"ip-address": f"fe80::{eui}", "ip-address-type": "ipv6", "prefix": 64})
        result.append({"hardware-address": iface.mac, "name": iface.name, "ip-addresses": addresses})
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
        for state in (secrets, images, world.overlays):
            state.pop(vm["vmid"], None)

    return ok(start_task(request, "qmdestroy", vm["vmid"], finish))


@routes.post(API + "/nodes/{node}/qemu/{vmid}/vncproxy")
async def vnc_proxy(request):
    vm = get_vm(request)
    require_unlocked(vm)
    if vm.get("template"):
        raise ApiError(500, "you can't open a console on a template")
    if vm["status"] != "running":
        raise ApiError(500, f"VM {vm['vmid']} not running")
    return ok(issue_ticket(request.match_info["node"], vm, request["token_user"]))


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
    stamp = time.strftime("%Y-%m-%d %H:%M:%S.000000000 +0000", time.gmtime())
    return "\n".join([f"--- /etc/network/interfaces\t{stamp}", f"+++ /etc/network/interfaces.new\t{stamp}",
                      *hunks]) + "\n"


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
    kind = params.get("type")
    if not iface or not kind:
        raise ApiError(400, "Parameter verification failed: iface and type are required")
    target = editable_networks()
    if any(n["iface"] == iface for n in target):
        raise ApiError(400, f"interface '{iface}' already exists")
    entry = {"iface": iface, "type": kind, "method": "manual", "method6": "manual", "families": ["inet"],
             "priority": len(target) + 1, "autostart": int(params.get("autostart") or 0)}
    if kind == "bridge":
        entry.update(bridge_ports=params.get("bridge_ports") or "", bridge_stp="off", bridge_fd="0")
    if params.get("comments"):
        # PVE reads comments back from the file, with their newline
        entry["comments"] = str(params["comments"]).rstrip("\n") + "\n"
    target.append(entry)
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
        networks = [{**n, "active": 1, "exists": 1} for n in pending_networks]
        pending_networks = None
    return ok(start_task(request, "srvreload", "networking", lambda: None))


@routes.get("/")
async def root(request):
    return web.Response(text="Proxmox VE API Mock Server")


async def not_implemented(request):
    print(f"Unhandled request: {request.method} {request.path}")
    return json_response({"data": None}, status=501,
                         reason=f"Method '{request.method} {request.path.removeprefix(API)}' not implemented")


def make_app() -> web.Application:
    app = web.Application(middlewares=[pve])
    app.router.add_get(API + "/nodes/{node}/qemu/{vmid}/vncwebsocket", console_handler(world))
    app.add_routes(routes)
    app.router.add_route("*", "/{tail:.*}", not_implemented)
    app.on_startup.append(start_ssh)
    return app


async def start_ssh(app: web.Application):
    print(await sshd.start(world))
    if world.ssh_port and world.ssh_port != 22:
        # The app shows `ssh -p PORT` for a bastion whose template carries this tag
        for vmid, config in configs.items():
            if vms[vmid].get("template") and "bastion" in str(config.get("tags", "")):
                config["tags"] = f"{config['tags']};ssh-port-{world.ssh_port}"


def main():
    context = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH)
    context.load_cert_chain(HERE / "ssl" / "cert.pem", HERE / "ssl" / "key.pem")
    print(f"Proxmox API Mock HTTPS Server running at https://{HOST}:{PORT}/")
    web.run_app(make_app(), host=HOST, port=PORT, ssl_context=context, print=None, access_log=None)


if __name__ == "__main__":
    main()
