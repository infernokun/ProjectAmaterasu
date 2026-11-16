"""Text sessions on the mock's machines, shared by the VNC console and the SSH server.

A session is line based: it shows a prompt, takes one line, and answers with the text to print. The console
starts at a getty login prompt; an SSH login starts straight in a shell. A shell can ssh to the machines it
reaches, which opens a nested session the same way a real hop through a bastion does."""

import ipaddress
import posixpath
import random
import re
import shlex
import time
from datetime import datetime, timezone

from machines import Machine

CLEAR = "\x0c"  # form feed: the terminal clears the screen


class Session:
    """One prompt at a time: feed() takes a line and returns what to print before the next prompt."""

    done = False

    @property
    def prompt(self) -> str:
        raise NotImplementedError

    @property
    def echo(self) -> bool:
        return True

    def feed(self, line: str) -> str:
        raise NotImplementedError

    def interrupt(self) -> str:
        return "^C\n"


# Login screens


class ConsoleLogin(Session):
    """The getty on a VM's console: login, password, then a shell; logging out comes back here."""

    def __init__(self, machine: Machine):
        self.machine = machine
        self.user: str | None = None
        self.shell: Session | None = None

    def banner(self) -> str:
        m = self.machine
        if m.windows:
            return f"{m.image.pretty}\n{m.hostname}\n\n"
        return f"{m.image.pretty} {m.hostname} tty1\n\n"

    @property
    def prompt(self) -> str:
        if self.shell:
            return self.shell.prompt
        if self.user is None:
            return "Username: " if self.machine.windows else f"{self.machine.hostname} login: "
        return "Password: "

    @property
    def echo(self) -> bool:
        return self.shell.echo if self.shell else self.user is None

    def feed(self, line: str) -> str:
        if self.shell:
            out = self.shell.feed(line)
            if self.shell.done:
                self.shell = None
                return out + "\n" + self.banner()
            return out
        if self.user is None:
            self.user = line.strip()
            return ""
        user, self.user = self.user, None
        if not user or not self.machine.check_login(user, line):
            return "\nLogin incorrect\n" if not self.machine.windows else "The user name or password is incorrect.\n"
        self.shell = open_shell(self.machine, user)
        return welcome(self.machine, user, "tty1")

    def interrupt(self) -> str:
        if self.shell:
            return self.shell.interrupt()
        self.user = None
        return "\n"


def welcome(machine: Machine, user: str, where: str) -> str:
    """The text after a login; where is the source address, or "tty1" on the console."""
    if machine.windows:
        return "\n"
    lines = []
    if machine.image.login_banner:
        lines += [machine.image.login_banner, ""]
    lines += [" * This system is simulated by the Amaterasu Proxmox mock. Type 'help' for what works.", ""]
    stamp = datetime.now().strftime("%a %b %d %H:%M:%S %Y")
    lines.append(f"Last login: {stamp} {'on' if where.startswith('tty') else 'from'} {where}")
    return "\n".join(lines) + "\n"


def open_shell(machine: Machine, user: str) -> Session:
    return Cmd(machine, user) if machine.windows else Shell(machine, user)


class SshClient(Session):
    """`ssh user@host` from inside a shell: up to three password tries, then a shell (or one command) there."""

    def __init__(self, target: Machine, user: str, host: str, command: str | None, source: Machine):
        self.target = target
        self.source = source
        self.user = user
        self.host = host
        self.command = command
        self.tries = 0
        self.shell: Session | None = None

    @property
    def prompt(self) -> str:
        return self.shell.prompt if self.shell else f"{self.user}@{self.host}'s password: "

    @property
    def echo(self) -> bool:
        return self.shell.echo if self.shell else False

    def feed(self, line: str) -> str:
        if self.shell:
            out = self.shell.feed(line)
            if self.shell.done:
                self.done = True
                return out + f"Connection to {self.host} closed.\n"
            return out
        if not self.target.running:
            self.done = True
            return f"\nssh: connect to host {self.host} port 22: No route to host\n"
        if not self.target.check_login(self.user, line):
            self.tries += 1
            if self.tries >= 3:
                self.done = True
                return f"\n{self.user}@{self.host}: Permission denied (publickey,password).\n"
            return "\nPermission denied, please try again.\n"
        shell = open_shell(self.target, self.user)
        if self.command:
            self.done = True
            return "\n" + shell.feed(self.command)
        self.shell = shell
        return "\n" + welcome(self.target, self.user, self.source.address_on(self.target))

    def interrupt(self) -> str:
        if self.shell:
            return self.shell.interrupt()
        self.done = True
        return "^C\n"


# The shells


class BaseShell(Session):
    def __init__(self, machine: Machine, user: str):
        self.machine = machine
        self.user = user
        self.cwd = machine.home(user)
        self.child: Session | None = None
        self.history: list[str] = []

    @property
    def prompt(self) -> str:
        return self.child.prompt if self.child else self.own_prompt()

    def own_prompt(self) -> str:
        raise NotImplementedError

    @property
    def echo(self) -> bool:
        return self.child.echo if self.child else True

    def interrupt(self) -> str:
        if self.child:
            out = self.child.interrupt()
            if self.child.done:
                self.child = None
            return out
        return "^C\n"

    def feed(self, line: str) -> str:
        if self.child:
            out = self.child.feed(line)
            if self.child.done:
                self.child = None
            return out
        if not self.machine.running:
            self.done = True
            return ""
        line = line.strip()
        if not line:
            return ""
        self.history.append(line)
        return self.run(line)

    def run(self, line: str) -> str:
        raise NotImplementedError

    # Files, with the machine's writable overlay on top of its image

    def overlay(self) -> dict[str, str | None]:
        return self.machine.world.overlays.setdefault(self.machine.vmid, {})

    def all_files(self) -> dict[str, str]:
        files = self.machine.files()
        for path, content in self.overlay().items():
            if content is None:
                files.pop(path, None)
            else:
                files[path] = content
        return files

    def resolve_ip(self, host: str):
        return self.machine.resolve(host)

    # Network tools shared by both shells

    def ssh(self, args: list[str]) -> str:
        user, port, rest = self.user, 22, []
        i = 0
        while i < len(args):
            a = args[i]
            if a in ("-p", "-l", "-i", "-o", "-J", "-L", "-D", "-F") and i + 1 < len(args):
                if a == "-p":
                    port = int(args[i + 1]) if args[i + 1].isdigit() else 22
                elif a == "-l":
                    user = args[i + 1]
                i += 2
                continue
            if a.startswith("-"):
                i += 1
                continue
            rest = args[i:]
            break
        if not rest:
            return "usage: ssh [-l login_name] [-p port] destination [command]\n"
        dest, command = rest[0], " ".join(rest[1:]) or None
        if "@" in dest:
            user, dest = dest.rsplit("@", 1)
        target = self.machine.resolve(dest)
        if not target:
            if not looks_like_ip(dest):
                return f"ssh: Could not resolve hostname {dest}: Name or service not known\n"
            return f"ssh: connect to host {dest} port {port}: No route to host\n"
        if target.image.services.get(port) != "ssh":
            return f"ssh: connect to host {dest} port {port}: Connection refused\n"
        self.child = SshClient(target, user, dest, command, self.machine)
        return ""

    def ping_lines(self, host: str, count: int) -> tuple[str | None, list[str], int]:
        """The target's address, one reply line per packet, and how many came back."""
        target = self.machine.resolve(host)
        address = target.address_on(self.machine) if target else (host if looks_like_ip(host) else None)
        if address is None:
            return None, [], 0
        if not target:
            own = next((i.ip for i in self.machine.interfaces
                        if ipaddress.ip_address(address) in ipaddress.ip_interface(f"{i.ip}/{i.prefix}").network),
                       next((i.ip for i in self.machine.interfaces if i.lab), "127.0.0.1"))
            return address, [f"From {own} icmp_seq={n} Destination Host Unreachable" for n in range(1, count + 1)], 0
        ttl = 128 if target.windows else 64
        replies = [f"64 bytes from {address}: icmp_seq={n} ttl={ttl} time={random.uniform(0.2, 0.9):.3f} ms"
                   for n in range(1, count + 1)]
        return address, replies, count

    def http_get(self, url: str) -> tuple[str | None, str]:
        """(page, error) for a GET from this machine."""
        match = re.match(r"^(?:(https?)://)?([^/:]+)(?::(\d+))?(/.*)?$", url)
        if not match:
            return None, f"Could not parse {url}"
        scheme, host, port_text, _ = match.groups()
        port = int(port_text) if port_text else (443 if scheme == "https" else 80)
        target = self.machine.resolve(host)
        if not target:
            if not looks_like_ip(host):
                return None, f"Could not resolve host: {host}"
            return None, f"Failed to connect to {host} port {port} after 3001 ms: No route to host"
        page = target.web_page(port)
        if page is None:
            return None, f"Failed to connect to {host} port {port} after 0 ms: Connection refused"
        return page, ""


class Shell(BaseShell):
    """A bash-like shell on a Linux image."""

    COMMANDS = ("cat cd clear date echo exit grep head help history hostname id ip logout ls nmap ping ps pwd "
                "rm ss ssh su sudo sysctl tail touch uname uptime w wc wget whoami which curl").split()

    def own_prompt(self) -> str:
        home = self.machine.home(self.user)
        where = "~" + self.cwd[len(home):] if self.cwd == home or self.cwd.startswith(home + "/") else self.cwd
        return f"{self.user}@{self.machine.hostname}:{where}{'#' if self.user == 'root' else '$'} "

    def has(self, tool: str) -> bool:
        return tool in self.machine.image.tools

    def run(self, line: str) -> str:
        out = []
        for part in split_unquoted(line, ";"):
            part = part.strip()
            if part:
                out.append(self.pipeline(part))
            if self.done or self.child:
                break
        return "".join(out)

    def pipeline(self, line: str) -> str:
        redirect = None
        match = re.match(r"^(.*?)\s*(>>?)\s*(\S+)\s*$", line)
        if match and "'" not in match.group(1)[-1:]:
            line, redirect = match.group(1), (match.group(2), match.group(3))
        data = None
        for stage in split_unquoted(line, "|"):
            data = self.command(stage.strip(), data)
            if self.done or self.child:
                return data or ""
        if redirect:
            mode, target = redirect
            path = self.path(target)
            if not self.can_write(path):
                return f"-bash: {target}: Permission denied\n"
            files = self.all_files()
            self.overlay()[path] = (files.get(path, "") if mode == ">>" else "") + (data or "")
            return ""
        return data or ""

    def command(self, text: str, stdin: str | None) -> str:
        try:
            args = shlex.split(text)
        except ValueError:
            return "-bash: syntax error: unexpected end of file\n"
        if not args:
            return ""
        name, rest = args[0], args[1:]
        handler = getattr(self, "cmd_" + name, None)
        tool_needed = name in ("curl", "wget", "ping", "ssh", "ip", "ss", "nmap")
        if handler is None or (tool_needed and not self.has(name)):
            return f"-bash: {name}: command not found\n"
        return handler(rest, stdin)

    # Paths and permissions

    def path(self, target: str) -> str:
        home = self.machine.home(self.user)
        if target == "~" or target.startswith("~/"):
            target = home + target[1:]
        return posixpath.normpath(posixpath.join(self.cwd, target)) if target else self.cwd

    def can_read(self, path: str) -> bool:
        if self.user == "root":
            return True
        if path == "/etc/shadow" or path == "/root" or path.startswith("/root/"):
            return False
        if path.startswith("/home/") and not (path + "/").startswith(f"/home/{self.user}/"):
            return False
        return True

    def can_write(self, path: str) -> bool:
        if self.user == "root":
            return True
        return path.startswith(self.machine.home(self.user) + "/") or path.startswith("/tmp/")

    def is_dir(self, path: str, files: dict[str, str]) -> bool:
        prefix = path.rstrip("/") + "/"
        return path == "/" or any(p.startswith(prefix) for p in files) or path in ("/tmp", "/home", "/root", "/etc")

    def listing(self, path: str, files: dict[str, str]) -> list[str]:
        prefix = "/" if path == "/" else path.rstrip("/") + "/"
        names = {p[len(prefix):].split("/")[0] for p in files if p.startswith(prefix)}
        if path == "/":
            names |= {"bin", "etc", "home", "root", "tmp", "usr", "var"}
        return sorted(n for n in names if n)

    # Commands

    def cmd_help(self, args, stdin):
        tools = sorted(set(self.COMMANDS) - {"curl", "wget", "ping", "ssh", "ip", "ss", "nmap"}
                       | set(self.machine.image.tools))
        return ("This is a simulated machine from the Amaterasu Proxmox mock. These commands work here:\n  "
                + " ".join(tools) + "\nPipes (|), ; and > redirection work too.\n")

    def cmd_whoami(self, args, stdin):
        return self.user + "\n"

    def cmd_id(self, args, stdin):
        if self.user == "root":
            return "uid=0(root) gid=0(root) groups=0(root)\n"
        users = [u for u in self.machine.accounts if u != "root"]
        uid = 1000 + (users.index(self.user) if self.user in users else 0)
        groups = f"{uid}({self.user})" + (",27(sudo)" if self.user in self.machine.image.sudoers else "")
        return f"uid={uid}({self.user}) gid={uid}({self.user}) groups={groups}\n"

    def cmd_hostname(self, args, stdin):
        if args and args[0] in ("-I", "-i"):
            return " ".join(i.ip for i in self.machine.interfaces) + " \n"
        return self.machine.hostname + "\n"

    def cmd_uname(self, args, stdin):
        k = self.machine.image.kernel
        if "-a" in args:
            return f"Linux {self.machine.hostname} {k} #1 SMP x86_64 GNU/Linux\n"
        if "-r" in args:
            return k + "\n"
        if "-n" in args:
            return self.machine.hostname + "\n"
        return "Linux\n"

    def cmd_pwd(self, args, stdin):
        return self.cwd + "\n"

    def cmd_cd(self, args, stdin):
        target = self.path(args[0] if args else "~")
        files = self.all_files()
        if not self.is_dir(target, files):
            what = "Not a directory" if target in files else "No such file or directory"
            return f"-bash: cd: {args[0]}: {what}\n"
        if not self.can_read(target):
            return f"-bash: cd: {args[0]}: Permission denied\n"
        self.cwd = target
        return ""

    def cmd_ls(self, args, stdin):
        flags = "".join(a[1:] for a in args if a.startswith("-"))
        targets = [a for a in args if not a.startswith("-")] or ["."]
        files = self.all_files()
        out = []
        for t in targets:
            path = self.path(t)
            if path in files:
                out.append(self.long_entry(posixpath.basename(path), files[path], False) if "l" in flags else t)
                continue
            if not self.is_dir(path, files):
                out.append(f"ls: cannot access '{t}': No such file or directory")
                continue
            if not self.can_read(path):
                out.append(f"ls: cannot open directory '{t}': Permission denied")
                continue
            names = [n for n in self.listing(path, files) if "a" in flags or not n.startswith(".")]
            if "l" in flags:
                out.append(f"total {len(names) * 4}")
                for n in names:
                    full = posixpath.join(path, n)
                    out.append(self.long_entry(n, files.get(full, ""), full not in files))
            elif names:
                out.append("  ".join(names))
        return "\n".join(out) + "\n" if out else ""

    def long_entry(self, name: str, content: str, directory: bool) -> str:
        owner = self.user if self.cwd.startswith(self.machine.home(self.user)) else "root"
        mode = "drwxr-xr-x" if directory else "-rw-r--r--"
        size = 4096 if directory else len(content.encode())
        return f"{mode} 1 {owner} {owner} {size:>5} Nov 16 09:00 {name}"

    def read(self, target: str) -> tuple[str | None, str]:
        path = self.path(target)
        files = self.all_files()
        if not self.can_read(path):
            return None, f"{target}: Permission denied"
        if path not in files:
            if self.is_dir(path, files):
                return None, f"{target}: Is a directory"
            return None, f"{target}: No such file or directory"
        if not self.can_read(path):
            return None, f"{target}: Permission denied"
        return files[path], ""

    def cmd_cat(self, args, stdin):
        if not args:
            return stdin or ""
        out = []
        for a in args:
            content, problem = self.read(a)
            out.append(content if content is not None else f"cat: {problem}\n")
        return "".join(out)

    def text_filter(self, name, args, stdin, keep):
        files = [a for a in args if not a.startswith("-") and not re.match(r"^\d+$", a)]
        texts = []
        if files:
            for f in files:
                content, problem = self.read(f)
                if content is None:
                    return f"{name}: {problem}\n"
                texts.append(content)
        else:
            texts.append(stdin or "")
        return keep("".join(texts).splitlines())

    def cmd_head(self, args, stdin):
        n = next((int(a.lstrip("-")) for a in args if re.match(r"^-\d+$", a)), 10)
        return self.text_filter("head", args, stdin, lambda lines: "".join(x + "\n" for x in lines[:n]))

    def cmd_tail(self, args, stdin):
        n = next((int(a.lstrip("-")) for a in args if re.match(r"^-\d+$", a)), 10)
        return self.text_filter("tail", args, stdin, lambda lines: "".join(x + "\n" for x in lines[-n:]))

    def cmd_wc(self, args, stdin):
        return self.text_filter("wc", args, stdin, lambda lines: f"{len(lines)} {sum(len(x.split()) for x in lines)}"
                                f" {sum(len(x) + 1 for x in lines)}\n")

    def cmd_grep(self, args, stdin):
        flags = "".join(a[1:] for a in args if a.startswith("-"))
        rest = [a for a in args if not a.startswith("-")]
        if not rest:
            return "Usage: grep [OPTION]... PATTERNS [FILE]...\n"
        pattern, files = rest[0], rest[1:]
        try:
            regex = re.compile(pattern, re.I if "i" in flags else 0)
        except re.error:
            regex = re.compile(re.escape(pattern), re.I if "i" in flags else 0)
        out = []
        sources = [(f, *self.read(f)) for f in files] if files else [("", stdin or "", "")]
        for name, content, problem in sources:
            if content is None:
                out.append(f"grep: {problem}")
                continue
            for line in content.splitlines():
                if bool(regex.search(line)) != ("v" in flags):
                    out.append(f"{name}:{line}" if len(files) > 1 else line)
        return "\n".join(out) + "\n" if out else ""

    def cmd_echo(self, args, stdin):
        return " ".join(a for a in args if a != "-e") + "\n"

    def cmd_touch(self, args, stdin):
        for a in args:
            path = self.path(a)
            if not self.can_write(path):
                return f"touch: cannot touch '{a}': Permission denied\n"
            self.overlay().setdefault(path, self.all_files().get(path, ""))
        return ""

    def cmd_rm(self, args, stdin):
        for a in (x for x in args if not x.startswith("-")):
            path = self.path(a)
            if path not in self.all_files():
                return f"rm: cannot remove '{a}': No such file or directory\n"
            if not self.can_write(path):
                return f"rm: cannot remove '{a}': Permission denied\n"
            self.overlay()[path] = None
        return ""

    def cmd_clear(self, args, stdin):
        return CLEAR

    def cmd_exit(self, args, stdin):
        self.done = True
        return "logout\n"

    cmd_logout = cmd_exit

    def cmd_history(self, args, stdin):
        return "".join(f"{n:>5}  {line}\n" for n, line in enumerate(self.history, 1))

    def cmd_date(self, args, stdin):
        return datetime.now(timezone.utc).strftime("%a %b %d %H:%M:%S UTC %Y") + "\n"

    def cmd_uptime(self, args, stdin):
        minutes = self.machine.uptime // 60
        span = f"{minutes // 60}:{minutes % 60:02d}" if minutes >= 60 else f"{minutes} min"
        return f" {datetime.now().strftime('%H:%M:%S')} up {span},  1 user,  load average: 0.00, 0.01, 0.00\n"

    def cmd_w(self, args, stdin):
        return self.cmd_uptime(args, stdin) + f"USER     TTY      FROM             LOGIN@   IDLE\n{self.user:<8} pts/0    -                09:00    0.00s\n"

    def cmd_ps(self, args, stdin):
        rows = ["    PID TTY          TIME CMD", "      1 ?        00:00:01 systemd"]
        if 22 in self.machine.image.services:
            rows.append("    612 ?        00:00:00 sshd")
        if 80 in self.machine.image.services:
            rows.append("    655 ?        00:00:00 nginx" if self.machine.image.key != "alpine" else "    655 ?        00:00:00 httpd")
        rows += ["   1401 pts/0    00:00:00 bash", "   1422 pts/0    00:00:00 ps"]
        return "\n".join(rows) + "\n"

    def cmd_which(self, args, stdin):
        out = []
        for a in args:
            if a in self.COMMANDS and (a not in ("curl", "wget", "ping", "ssh", "ip", "ss", "nmap") or self.has(a)):
                out.append(f"/usr/bin/{a}")
        return "\n".join(out) + "\n" if out else ""

    def cmd_sysctl(self, args, stdin):
        value = "0" if self.machine.is_bastion else "1"
        if any("ip_forward" in a for a in args):
            if any("=" in a for a in args) and self.user != "root":
                return "sysctl: permission denied on key \"net.ipv4.ip_forward\"\n"
            return f"net.ipv4.ip_forward = {value}\n"
        return "usage: sysctl [options] [variable[=value] ...]\n"

    def cmd_sudo(self, args, stdin):
        if self.user != "root" and self.user not in self.machine.image.sudoers:
            return f"{self.user} is not in the sudoers file.  This incident will be reported.\n"
        if not args:
            return "usage: sudo -h | -K | -k | -V\n"
        if args[0] in ("-i", "-s", "su", "bash", "-iu"):
            self.child = Shell(self.machine, "root")
            return ""
        return Shell(self.machine, "root").run(shlex.join(args))

    def cmd_su(self, args, stdin):
        if self.user == "root":
            target = next((a for a in args if not a.startswith("-")), "root")
            if target not in self.machine.accounts and target != "root":
                return f"su: user {target} does not exist\n"
            self.child = Shell(self.machine, target)
            return ""
        return "Password: \nsu: Authentication failure\n"

    def cmd_ssh(self, args, stdin):
        return self.ssh(args)

    def cmd_ip(self, args, stdin):
        what = args[0] if args else ""
        if what in ("a", "addr", "address"):
            rows = ["1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN group default qlen 1000",
                    "    link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00",
                    "    inet 127.0.0.1/8 scope host lo"]
            for n, i in enumerate(self.machine.interfaces, 2):
                net = ipaddress.ip_interface(f"{i.ip}/{i.prefix}").network
                rows += [f"{n}: {i.name}: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc fq_codel state UP group default qlen 1000",
                         f"    link/ether {i.mac} brd ff:ff:ff:ff:ff:ff",
                         f"    inet {i.ip}/{i.prefix} brd {net.broadcast_address} scope global {i.name}"]
            return "\n".join(rows) + "\n"
        if what in ("r", "route"):
            rows = []
            for i in self.machine.interfaces:
                net = ipaddress.ip_interface(f"{i.ip}/{i.prefix}").network
                if not i.lab and not rows:
                    rows.append(f"default via {net.network_address + 1} dev {i.name} proto dhcp src {i.ip} metric 100")
                rows.append(f"{net} dev {i.name} proto kernel scope link src {i.ip}")
            return "\n".join(rows) + "\n"
        return "Usage: ip [ OPTIONS ] OBJECT { COMMAND | help }\n       where  OBJECT := { address | route }\n"

    def cmd_ss(self, args, stdin):
        rows = ["State  Recv-Q Send-Q Local Address:Port Peer Address:Port Process"]
        for port in sorted(self.machine.image.services):
            rows.append(f"LISTEN 0      128          0.0.0.0:{port:<5}      0.0.0.0:*")
        return "\n".join(rows) + "\n"

    def cmd_ping(self, args, stdin):
        count, host, i = 4, None, 0
        while i < len(args):
            if args[i] == "-c" and i + 1 < len(args):
                count = max(1, min(int(args[i + 1]) if args[i + 1].isdigit() else 4, 20))
                i += 2
                continue
            if not args[i].startswith("-"):
                host = args[i]
            i += 1
        if not host:
            return "ping: usage error: Destination address required\n"
        address, replies, received = self.ping_lines(host, count)
        if address is None:
            return f"ping: {host}: Name or service not known\n"
        loss = 100 - received * 100 // count
        out = [f"PING {host} ({address}) 56(84) bytes of data.", *replies, "",
               f"--- {host} ping statistics ---",
               f"{count} packets transmitted, {received} received, {loss}% packet loss, time {count * 1000 - 1000}ms"]
        if received:
            out.append("rtt min/avg/max/mdev = 0.214/0.402/0.887/0.120 ms")
        return "\n".join(out) + "\n"

    def cmd_curl(self, args, stdin):
        urls = [a for a in args if not a.startswith("-")]
        if not urls:
            return "curl: try 'curl --help' or 'curl --manual' for more information\n"
        page, error = self.http_get(urls[0])
        if page is None:
            code = 6 if error.startswith("Could not resolve") else 7
            return f"curl: ({code}) {error}\n"
        if "-I" in args:
            return "HTTP/1.1 200 OK\nServer: nginx\nContent-Type: text/html\n\n"
        return page

    def cmd_wget(self, args, stdin):
        urls = [a for a in args if not a.startswith("-") and a != "-"]
        if not urls:
            return "wget: missing URL\n"
        page, error = self.http_get(urls[0])
        if page is None:
            return f"wget: unable to connect: {error}\n"
        if "-O" in args and args[args.index("-O") + 1:args.index("-O") + 2] == ["-"]:
            return page
        name = urls[0].rstrip("/").split("/")[-1] if urls[0].count("/") > 2 else "index.html"
        path = self.path(name)
        if not self.can_write(path):
            return f"wget: {name}: Permission denied\n"
        self.overlay()[path] = page
        return f"Connecting to {urls[0]}... connected.\nHTTP request sent, awaiting response... 200 OK\n'{name}' saved [{len(page)}]\n"

    def cmd_nmap(self, args, stdin):
        targets = [a for a in args if not a.startswith("-") and not re.match(r"^\d+(,\d+)*$", a)]
        if not targets:
            return "Nmap 7.93 ( https://nmap.org )\nUsage: nmap [Scan Type(s)] [Options] {target specification}\n"
        ping_only = "-sn" in args
        started = time.time()
        hosts = []
        try:
            network = ipaddress.ip_network(targets[0], strict=False)
        except ValueError:
            target = self.machine.resolve(targets[0])
            if not target:
                return f"Failed to resolve \"{targets[0]}\".\nNmap done: 0 IP addresses (0 hosts up) scanned\n"
            hosts.append((target.address_on(self.machine), target))
            network = None
        if network is not None:
            seen = set()
            for other, iface in self.machine.neighbours():
                if ipaddress.ip_address(iface.ip) in network and iface.ip not in seen:
                    seen.add(iface.ip)
                    hosts.append((iface.ip, other))
        out = [f"Starting Nmap 7.93 ( https://nmap.org ) at {datetime.now().strftime('%Y-%m-%d %H:%M %Z').strip()}"]
        for ip, host in sorted(hosts, key=lambda h: ipaddress.ip_address(h[0])):
            out.append(f"Nmap scan report for {ip}")
            out.append("Host is up (0.00031s latency).")
            if not ping_only:
                out.append("PORT     STATE SERVICE")
                for port, service in sorted(host.image.services.items()):
                    out.append(f"{f'{port}/tcp':<8} open  {service}")
            out.append("")
        total = network.num_addresses if network is not None else 1
        out.append(f"Nmap done: {total} IP address{'es' if total != 1 else ''} ({len(hosts)} host"
                   f"{'s' if len(hosts) != 1 else ''} up) scanned in {time.time() - started + 1.2:.2f} seconds")
        return "\n".join(out) + "\n"


class Cmd(BaseShell):
    """cmd.exe on the Windows image."""

    def own_prompt(self) -> str:
        return self.cwd.replace("/", "\\") + ">"

    def winpath(self, target: str) -> str:
        target = target.replace("\\", "/")
        if target.startswith("/"):
            target = self.cwd[:2] + target
        if re.match(r"^[A-Za-z]:", target):
            path = target
        else:
            path = posixpath.join(self.cwd, target)
        drive, rest = path[:2].upper(), path[2:] or "/"
        rest = posixpath.normpath(rest)
        return drive + (rest if rest != "." else "/")

    def find(self, path: str, files: dict[str, str]) -> str | None:
        lower = path.lower()
        return next((p for p in files if p.lower() == lower), None)

    def run(self, line: str) -> str:
        try:
            args = shlex.split(line, posix=False)
        except ValueError:
            args = line.split()
        name, rest = args[0].lower(), args[1:]
        files = self.all_files()
        if name in ("exit", "logout"):
            self.done = True
            return ""
        if name == "help":
            return ("This is a simulated Windows machine from the Amaterasu Proxmox mock. These commands work here:\n"
                    "  cd cls curl dir echo exit hostname ipconfig net ping ssh systeminfo type ver whoami\n")
        if name == "whoami":
            return f"{self.machine.hostname.lower()}\\{self.user.lower()}\n"
        if name == "hostname":
            return self.machine.hostname + "\n"
        if name == "ver":
            return "\nMicrosoft Windows [Version 10.0.20348.2849]\n"
        if name == "cls":
            return CLEAR
        if name == "echo":
            return " ".join(rest) + "\n"
        if name == "cd":
            if not rest:
                return self.cwd.replace("/", "\\") + "\n"
            path = self.winpath(" ".join(rest))
            prefix = path.lower().rstrip("/") + "/"
            if not any(p.lower().startswith(prefix) for p in files) and path.lower() not in ("c:/", "c:/users"):
                return "The system cannot find the path specified.\n"
            self.cwd = path.rstrip("/") or path
            return ""
        if name == "dir":
            path = self.winpath(rest[0]) if rest else self.cwd
            prefix = path.lower().rstrip("/") + "/"
            names = sorted({p[len(prefix):].split("/")[0] for p in files if p.lower().startswith(prefix)})
            out = [f" Directory of {path.replace('/', chr(92))}", ""]
            for n in names:
                full = prefix + n
                entry = self.find(full, files)
                size = f"{len(files[entry]):>14}" if entry else "    <DIR>     "
                out.append(f"11/16/2026  09:00 AM {size} {n}")
            out.append(f"               {len(names)} File(s)")
            return "\n".join(out) + "\n"
        if name == "type":
            if not rest:
                return "The syntax of the command is incorrect.\n"
            entry = self.find(self.winpath(rest[0]), files)
            return files[entry] if entry else "The system cannot find the file specified.\n"
        if name == "ipconfig":
            out = ["", "Windows IP Configuration", ""]
            for i in self.machine.interfaces:
                net = ipaddress.ip_interface(f"{i.ip}/{i.prefix}").network
                out += [f"Ethernet adapter {i.name}:", "",
                        "   Connection-specific DNS Suffix  . :",
                        f"   IPv4 Address. . . . . . . . . . . : {i.ip}",
                        f"   Subnet Mask . . . . . . . . . . . : {net.netmask}", ""]
            return "\n".join(out) + "\n"
        if name == "systeminfo":
            return (f"\nHost Name:                 {self.machine.hostname.upper()}\n"
                    f"OS Name:                   Microsoft {self.machine.image.pretty}\n"
                    "OS Version:                10.0.20348 N/A Build 20348\n")
        if name == "net" and rest[:1] and rest[0].lower() == "user":
            users = "  ".join(self.machine.accounts)
            return f"\nUser accounts for \\\\{self.machine.hostname.upper()}\n\n{users}  Guest\nThe command completed successfully.\n\n"
        if name == "ping":
            host = next((a for a in rest if not a.startswith("-")), None)
            if not host:
                return "Usage: ping [-n count] target_name\n"
            address, replies, received = self.ping_lines(host, 4)
            if address is None:
                return f"Ping request could not find host {host}. Please check the name and try again.\n"
            target = self.machine.resolve(host)
            ttl = 128 if target and target.windows else 64
            lines = [f"Reply from {address}: bytes=32 time<1ms TTL={ttl}" if received else "Destination host unreachable."
                     for _ in replies]
            return f"\nPinging {address} with 32 bytes of data:\n" + "\n".join(lines) + (
                f"\n\nPing statistics for {address}:\n    Packets: Sent = 4, Received = {received}, Lost = {4 - received}\n")
        if name == "curl":
            urls = [a for a in rest if not a.startswith("-")]
            if not urls:
                return "curl: try 'curl --help' for more information\n"
            page, error = self.http_get(urls[0])
            return page if page is not None else f"curl: (7) {error}\n"
        if name == "ssh":
            return self.ssh(rest)
        return f"'{args[0]}' is not recognized as an internal or external command,\noperable program or batch file.\n"


def split_unquoted(text: str, sep: str) -> list[str]:
    parts, current, quote = [], "", None
    for ch in text:
        if quote:
            current += ch
            if ch == quote:
                quote = None
        elif ch in "'\"":
            quote = ch
            current += ch
        elif ch == sep:
            parts.append(current)
            current = ""
        else:
            current += ch
    parts.append(current)
    return parts


def looks_like_ip(text: str) -> bool:
    try:
        ipaddress.ip_address(text)
        return True
    except ValueError:
        return False
