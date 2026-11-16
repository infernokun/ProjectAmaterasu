"""The machines behind the mock's VMs: what each template's image contains, and what a running copy looks like
from inside (accounts, files, network interfaces, services and which other machines it can reach).

Lab NICs sit on private abrN bridges, so a machine reaches another one only through a bridge they share, the
way the lab isolation works on a real node. A bastion's uplink answers on the developer machine's own address,
where the mock's SSH server listens, so the address the app shows can actually be used."""

import ipaddress
import re
import time
from dataclasses import dataclass, field

NIC_KEY = re.compile(r"^net\d+$")
MAC_VALUE = re.compile(r"=([0-9A-Fa-f:]{17})")
BRIDGE = re.compile(r"bridge=([^,]+)")
LAB_BRIDGE = re.compile(r"^abr\d+$")
STATIC_IP = re.compile(r"ip=([\d.]+)/(\d+)")


@dataclass
class Image:
    """What a template's disk holds."""

    key: str
    pretty: str
    family: str  # "linux" or "windows"
    kernel: str
    accounts: dict[str, str]  # baked-in users and passwords; a template's ciuser is added per copy
    services: dict[int, str] = field(default_factory=lambda: {22: "ssh"})
    tools: frozenset[str] = frozenset()
    sudoers: frozenset[str] = frozenset()
    shell: str = "/bin/bash"
    files: dict[str, str] = field(default_factory=dict)  # extra files, {path: content}; {host} is filled in
    web: str | None = None  # the page its web server sends, if it has one
    login_banner: str = ""


NGINX_PAGE = """<!DOCTYPE html>
<html>
<head><title>Welcome to nginx!</title></head>
<body>
<h1>Welcome to nginx!</h1>
<p>If you see this page, the nginx web server is successfully installed and working.</p>
<p><em>Thank you for using nginx.</em></p>
</body>
</html>
"""

STATUS_PAGE = """<!DOCTYPE html>
<html>
<head><title>{host} status</title></head>
<body>
<h1>{host}</h1>
<p>Internal status page. Not reachable from outside the lab network.</p>
<!-- FLAG{{mock-web-{host}}} -->
</body>
</html>
"""

IMAGES: dict[str, Image] = {
    "ubuntu": Image(
        "ubuntu", "Ubuntu 20.04.6 LTS", "linux", "5.4.0-182-generic", {"ubuntu": "ubuntu"},
        tools=frozenset({"curl", "wget", "ping", "ssh", "ip", "ss"}), sudoers=frozenset({"ubuntu"}),
        login_banner="Welcome to Ubuntu 20.04.6 LTS (GNU/Linux 5.4.0-182-generic x86_64)",
        files={"/home/ubuntu/notes.txt": "Remember to rotate the backup keys on {host}.\n"}),
    "centos": Image(
        "centos", "CentOS Linux 8", "linux", "4.18.0-348.7.1.el8_5.x86_64", {"centos": "centos"},
        tools=frozenset({"curl", "ping", "ssh", "ip", "ss"}), sudoers=frozenset({"centos"}),
        files={"/etc/redhat-release": "CentOS Linux release 8.5.2111\n"}),
    "debian11": Image(
        "debian11", "Debian GNU/Linux 11 (bullseye)", "linux", "5.10.0-28-amd64", {"debian": "debian"},
        services={22: "ssh", 80: "http"}, tools=frozenset({"curl", "wget", "ping", "ssh", "ip", "ss"}),
        sudoers=frozenset({"debian"}), web=NGINX_PAGE,
        files={"/var/www/html/index.nginx-debian.html": NGINX_PAGE}),
    "windows": Image(
        "windows", "Windows Server 2022 Standard", "windows", "10.0.20348", {"Administrator": "Amaterasu!2022"},
        services={22: "ssh", 135: "msrpc", 445: "microsoft-ds", 3389: "ms-wbt-server"},
        tools=frozenset({"ping", "ssh", "curl"}), shell="cmd.exe",
        files={"C:/Users/Administrator/Desktop/readme.txt": "Domain join pending. Ask IT before rebooting {host}.\n"}),
    "alpine": Image(
        "alpine", "Alpine Linux v3.20", "linux", "6.6.58-0-virt", {"root": "alpine"},
        services={22: "ssh", 80: "http"}, tools=frozenset({"wget", "ping", "ip"}), shell="/bin/ash",
        web=STATUS_PAGE, files={"/var/www/index.html": STATUS_PAGE, "/root/flag.txt": "FLAG{{mock-root-{host}}}\n"}),
    "bastion": Image(
        "bastion", "Debian GNU/Linux 12 (bookworm)", "linux", "6.1.0-27-cloud-amd64", {},
        tools=frozenset({"curl", "wget", "ping", "ssh", "ip", "ss", "nmap"}), sudoers=frozenset({"player"}),
        login_banner="Bastion host. Everything you do here is logged.",
        files={"/etc/sysctl.d/99-bastion.conf": "net.ipv4.ip_forward = 0\n"}),
    "server": Image(
        "server", "Ubuntu 22.04.5 LTS", "linux", "5.15.0-125-generic", {"admin": "admin"},
        tools=frozenset({"curl", "wget", "ping", "ssh", "ip", "ss"}), sudoers=frozenset({"admin"}),
        login_banner="Welcome to Ubuntu 22.04.5 LTS (GNU/Linux 5.15.0-125-generic x86_64)"),
}

# Seeded templates and the image each one's disk holds; any other VM runs the generic server image
TEMPLATE_IMAGES = {9000: "ubuntu", 9001: "centos", 9002: "debian11", 9003: "windows", 9004: "alpine", 9005: "bastion"}


@dataclass
class Interface:
    name: str
    nic: str
    mac: str
    bridge: str
    ip: str
    prefix: int

    @property
    def lab(self) -> bool:
        return bool(LAB_BRIDGE.match(self.bridge))


class World:
    """The mock's VMs seen as machines. Holds references to the server's state, so it always reads it live."""

    def __init__(self, vms: dict, configs: dict, started_at: dict, secrets: dict, images: dict):
        self.vms = vms
        self.configs = configs
        self.started_at = started_at
        self.secrets = secrets  # vmid -> cipassword as set, which the API only shows masked
        self.images = images  # vmid -> image key, copied from template to clone
        self.overlays: dict[int, dict[str, str | None]] = {}  # files written on a running copy; None is deleted
        self.ssh_address: str | None = None
        self.ssh_port: int | None = None

    def machine(self, vmid: int) -> "Machine | None":
        vm = self.vms.get(vmid)
        if not vm or vmid not in self.configs:
            return None
        return Machine(self, vm, self.configs[vmid])

    def running(self) -> list["Machine"]:
        return [m for vmid, vm in list(self.vms.items())
                if vm["status"] == "running" and (m := self.machine(vmid))]

    def subnet(self, bridge: str) -> ipaddress.IPv4Network:
        """A lab bridge's network: the one its VMs' static addresses are in, else 172.16.N.0/24 of its own."""
        for config in list(self.configs.values()):
            for nic in (k for k in config if NIC_KEY.match(k)):
                found = BRIDGE.search(str(config[nic]))
                fixed = STATIC_IP.search(str(config.get(f"ipconfig{nic[3:]}") or ""))
                if found and found[1] == bridge and fixed:
                    return ipaddress.ip_interface(f"{fixed[1]}/{fixed[2]}").network
        number = int(bridge[3:]) if bridge[3:].isdigit() else 0
        return ipaddress.ip_network(f"172.16.{number % 254 + 1}.0/24")

    def bastions_for(self, user: str, password: str) -> list["Machine"]:
        """Running bastions that let this user in with this password: who an SSH login to the shared address is for."""
        return [m for m in self.running() if m.is_bastion and m.check_login(user, password)]


class Machine:
    def __init__(self, world: World, vm: dict, config: dict):
        self.world = world
        self.vm = vm
        self.config = config
        self.vmid: int = vm["vmid"]
        self.image = IMAGES[world.images.get(self.vmid, "server")]

    # Identity

    @property
    def hostname(self) -> str:
        name = re.sub(r"[^A-Za-z0-9-]", "-", str(self.config.get("name") or self.vm["name"])).strip("-")
        return name[:63] or f"vm{self.vmid}"

    @property
    def windows(self) -> bool:
        return self.image.family == "windows"

    @property
    def tags(self) -> list[str]:
        return [t for t in re.split(r"[;,\s]+", str(self.config.get("tags") or "")) if t]

    @property
    def is_bastion(self) -> bool:
        return "bastion" in (t.lower() for t in self.tags)

    @property
    def running(self) -> bool:
        live = self.world.vms.get(self.vmid)
        return bool(live) and live["status"] == "running"

    @property
    def uptime(self) -> int:
        started = self.world.started_at.get(self.vmid)
        return int(time.time() - started / 1000) if started else 0

    # Accounts

    @property
    def accounts(self) -> dict[str, str | None]:
        accounts: dict[str, str | None] = dict(self.image.accounts)
        ciuser = self.config.get("ciuser")
        if ciuser:
            # cloud-init's user, with the copy's password; without one it only takes keys
            accounts[str(ciuser)] = self.world.secrets.get(self.vmid)
        return accounts

    def check_login(self, user: str, password: str) -> bool:
        expected = self.accounts.get(user)
        return expected is not None and password == expected

    def home(self, user: str) -> str:
        if self.windows:
            return f"C:/Users/{user}"
        return "/root" if user == "root" else f"/home/{user}"

    # Network

    @property
    def interfaces(self) -> list[Interface]:
        found = []
        nics = sorted(k for k in self.config if NIC_KEY.match(k))
        for index, nic in enumerate(nics):
            value = str(self.config[nic])
            mac = MAC_VALUE.search(value)
            bridge = BRIDGE.search(value)
            bridge_name = bridge[1] if bridge else ""
            fixed = STATIC_IP.search(str(self.config.get(f"ipconfig{nic[3:]}") or ""))
            if fixed:
                ip, prefix = fixed[1], int(fixed[2])
            elif LAB_BRIDGE.match(bridge_name):
                # No DHCP on a lab bridge: the template's image picks an address in the lab's network
                network = self.world.subnet(bridge_name)
                ip, prefix = str(network.network_address + 100 + self.vmid % 100), network.prefixlen
            elif self.is_bastion and nic == "net0" and self.world.ssh_address:
                # The uplink's DHCP lease is the developer machine's address, where the SSH server answers
                ip, prefix = self.world.ssh_address, 24
            else:
                ip, prefix = f"10.0.0.{20 + self.vmid % 200}", 24
            name = f"Ethernet{index}" if self.windows else f"eth{index}"
            found.append(Interface(name, nic, mac[1].lower() if mac else "", bridge_name, ip, prefix))
        return found

    def resolve(self, host: str) -> "Machine | None":
        """The running machine this one reaches at host (an address or a host name) through a shared bridge."""
        host = host.strip().lower()
        if host in ("localhost", "127.0.0.1", self.hostname.lower()) or any(i.ip == host for i in self.interfaces):
            return self
        mine = {i.bridge for i in self.interfaces}
        for other in self.world.running():
            if other.vmid == self.vmid:
                continue
            for iface in other.interfaces:
                if iface.bridge in mine and (iface.ip == host or other.hostname.lower() == host):
                    return other
        return None

    def neighbours(self) -> list[tuple["Machine", Interface]]:
        """Every running machine on a bridge this one shares, with the interface it is reached on."""
        mine = {i.bridge for i in self.interfaces}
        found = []
        for other in self.world.running():
            for iface in other.interfaces:
                if iface.bridge in mine:
                    found.append((other, iface))
        return found

    def address_on(self, other: "Machine") -> str:
        """This machine's address as the other one sees it."""
        theirs = {i.bridge for i in other.interfaces}
        for iface in self.interfaces:
            if iface.bridge in theirs:
                return iface.ip
        return self.interfaces[0].ip if self.interfaces else "127.0.0.1"

    # Files

    def files(self) -> dict[str, str]:
        host = self.hostname
        if self.windows:
            files = {"C:/Windows/System32/drivers/etc/hosts": "127.0.0.1 localhost\n"}
            for user in self.accounts:
                files[f"C:/Users/{user}/Desktop/.keep"] = ""
        else:
            passwd = ["root:x:0:0:root:/root:/bin/bash"]
            for n, user in enumerate(u for u in self.accounts if u != "root"):
                passwd.append(f"{user}:x:{1000 + n}:{1000 + n}::/home/{user}:{self.image.shell}")
            files = {
                "/etc/hostname": host + "\n",
                "/etc/hosts": f"127.0.0.1 localhost\n127.0.1.1 {host}\n",
                "/etc/os-release": f'PRETTY_NAME="{self.image.pretty}"\nNAME="{self.image.pretty.split(" ")[0]}"\n',
                "/etc/passwd": "\n".join(passwd) + "\n",
                "/etc/shadow": "",
                "/root/.bashrc": "",
            }
            for user in self.accounts:
                home = self.home(user)
                files[f"{home}/.bashrc"] = ""
                files[f"{home}/README"] = (f"{host} is a simulated machine from the Amaterasu Proxmox mock.\n"
                                           "Commands that work here: run 'help'.\n")
        for path, content in self.image.files.items():
            files[path.replace("{host}", host)] = content.format(host=host)
        return files

    def web_page(self, port: int) -> str | None:
        if self.image.services.get(port) == "http" and self.image.web:
            return self.image.web.format(host=self.hostname)
        return None
