"""The SSH server behind the mock's bastions.

Every bastion's uplink address is the developer machine's own address, so one server on MOCK_SSH_PORT answers
for all of them; the user name and the copy's password pick the bastion. From there `ssh -J` reaches lab machines
and `ssh -L` reaches their web servers, both through the lab's private network as on a real node."""

import asyncio
import os
import socket
from pathlib import Path

import asyncssh
from machines import Machine, World
from shell import CLEAR, open_shell, welcome

HERE = Path(__file__).parent
KEY_FILE = HERE / "ssl" / "ssh_host_ed25519_key"


def host_key() -> asyncssh.SSHKey:
    """The server's host key, kept between runs so known_hosts stays valid."""
    if KEY_FILE.exists():
        return asyncssh.read_private_key(str(KEY_FILE))
    key = asyncssh.generate_private_key("ssh-ed25519")
    key.write_private_key(str(KEY_FILE))
    KEY_FILE.chmod(0o600)
    return key


def own_address() -> str | None:
    """This machine's address on its LAN: where a user's ssh can reach the mock. None if there is only loopback."""
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("192.0.2.1", 9))  # TEST-NET: nothing is sent, the kernel only picks the route
        address = probe.getsockname()[0]
    except OSError:
        return None
    finally:
        probe.close()
    return None if address.startswith("127.") or address == "0.0.0.0" else address


class MachineServer(asyncssh.SSHServer):
    """One SSH login. With a fixed machine it is the sshd of a lab machine reached through a bastion."""

    def __init__(self, world: World, key: asyncssh.SSHKey, fixed: Machine | None = None):
        self.world = world
        self.key = key
        self.fixed = fixed
        self.machine: Machine | None = None

    def begin_auth(self, username: str) -> bool:
        return True

    def password_auth_supported(self) -> bool:
        return True

    def validate_password(self, username: str, password: str) -> bool:
        if self.fixed:
            found = [self.fixed] if self.fixed.running and self.fixed.check_login(username, password) else []
        else:
            found = self.world.bastions_for(username, password)
        if found:
            self.machine = found[0]
            print(f"SSH login as {username} on VM {self.machine.vmid} ({self.machine.hostname})")
        return bool(found)

    def connection_requested(self, dest_host: str, dest_port: int, orig_host: str, orig_port: int):
        """ssh -J and ssh -L: reach a machine on the lab network from this one."""
        assert self.machine is not None
        target = self.machine.resolve(dest_host)
        if target is None:
            raise asyncssh.ChannelOpenError(asyncssh.OPEN_CONNECT_FAILED, "No route to host")
        service = target.image.services.get(dest_port)
        if service == "ssh":
            return Hop(self.world, self.key, target)
        if service == "http":
            return WebSession(target, dest_port)
        raise asyncssh.ChannelOpenError(asyncssh.OPEN_CONNECT_FAILED, "Connection refused")


async def run_session(process: asyncssh.SSHServerProcess) -> None:
    server: MachineServer = process.get_extra_info("connection").get_owner()
    machine, user = server.machine, process.get_extra_info("username")
    if machine is None:
        process.exit(1)
        return
    shell = open_shell(machine, user)
    if process.command is not None:
        process.stdout.write(shell.feed(process.command).replace(CLEAR, ""))
        process.exit(0)
        return
    channel = process.channel
    peer = process.get_extra_info("peername")
    where = peer[0] if isinstance(peer, tuple) and peer else "the bastion"
    process.stdout.write(welcome(machine, user, where))
    watcher = asyncio.ensure_future(drop_when_stopped(process, machine))
    while not shell.done and machine.running:
        if hasattr(channel, "set_echo"):
            channel.set_echo(shell.echo)
        process.stdout.write(shell.prompt)
        try:
            line = await process.stdin.readline()
        except asyncssh.BreakReceived:
            process.stdout.write(shell.interrupt())
            continue
        except (asyncssh.TerminalSizeChanged, asyncssh.SignalReceived):
            continue
        if line == "":
            if shell.echo:
                process.stdout.write("logout\n")
            break
        out = shell.feed(line.rstrip("\r\n"))
        process.stdout.write(out.replace(CLEAR, "\x1b[2J\x1b[H"))
    watcher.cancel()
    if not process.is_closing():
        process.exit(0)


async def drop_when_stopped(process: asyncssh.SSHServerProcess, machine: Machine) -> None:
    """A VM that shuts down takes its sshd with it: the session ends at once, as on a real machine."""
    while machine.running:
        await asyncio.sleep(1)
    process.stdout.write(f"\nConnection to {machine.hostname} closed by remote host.\n")
    process.exit(255)


class Hop(asyncssh.SSHTCPSession):
    """A direct-tcpip channel to port 22 of a lab machine: that machine's own sshd, over the channel."""

    def __init__(self, world: World, key: asyncssh.SSHKey, target: Machine):
        self.world = world
        self.key = key
        self.target = target
        self.writer: asyncio.StreamWriter | None = None
        self.queued: list[bytes] = []
        self.eof = False

    def connection_made(self, chan):
        self.chan = chan
        ours, theirs = socket.socketpair()
        asyncio.ensure_future(self.pump(ours))
        asyncio.ensure_future(self.serve(theirs))

    async def serve(self, sock: socket.socket):
        try:
            await asyncssh.run_server(
                sock, server_factory=lambda: MachineServer(self.world, self.key, self.target),
                server_host_keys=[self.key], process_factory=run_session, line_editor=True, keepalive_interval=0)
        except (asyncssh.Error, OSError):
            # The client hung up during the handshake, as a port probe through a tunnel does
            sock.close()

    async def pump(self, sock: socket.socket):
        reader, self.writer = await asyncio.open_connection(sock=sock)
        for data in self.queued:
            self.writer.write(data)
        self.queued.clear()
        if self.eof:
            self.writer.write_eof()
        while data := await reader.read(65536):
            self.chan.write(data)
        self.chan.close()

    def data_received(self, data, datatype):
        if self.writer:
            self.writer.write(data)
        else:
            self.queued.append(data)

    def eof_received(self):
        if self.writer:
            self.writer.write_eof()
        else:
            self.eof = True
        return True

    def connection_lost(self, exc):
        if self.writer:
            self.writer.close()


class WebSession(asyncssh.SSHTCPSession):
    """A direct-tcpip channel to a lab machine's web server: one HTTP answer, then close."""

    def __init__(self, target: Machine, port: int):
        self.target = target
        self.port = port
        self.request = b""

    def connection_made(self, chan):
        self.chan = chan

    def data_received(self, data, datatype):
        self.request += data
        if b"\r\n\r\n" not in self.request and b"\n\n" not in self.request:
            return
        method = self.request.split(b" ", 1)[0].decode(errors="replace")
        page = self.target.web_page(self.port) or ""
        if method in ("GET", "HEAD"):
            body = page.encode() if method == "GET" else b""
            head = (f"HTTP/1.1 200 OK\r\nServer: nginx\r\nContent-Type: text/html\r\n"
                    f"Content-Length: {len(page.encode())}\r\nConnection: close\r\n\r\n")
        else:
            body = b""
            head = "HTTP/1.1 405 Not Allowed\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        self.chan.write(head.encode() + body)
        self.chan.close()


async def start(world: World) -> str | None:
    """Starts the SSH server unless MOCK_SSH=0. Returns a line for the startup log."""
    if os.getenv("MOCK_SSH", "1") == "0":
        return "SSH: off (MOCK_SSH=0); bastion addresses are made up"
    address = os.getenv("MOCK_SSH_ADDRESS") or own_address()
    if not address:
        return "SSH: off, this machine has no LAN address; set MOCK_SSH_ADDRESS"
    port = int(os.getenv("MOCK_SSH_PORT", "2022"))
    key = host_key()
    try:
        await asyncssh.create_server(
            lambda: MachineServer(world, key), os.getenv("MOCK_SSH_BIND", address), port,
            server_host_keys=[key], process_factory=run_session, line_editor=True, keepalive_interval=0)
    except OSError as e:
        return f"SSH: off, could not listen on {address}:{port} ({e.strerror}); set MOCK_SSH_PORT"
    world.ssh_address, world.ssh_port = address, port
    return f"SSH: bastions answer on {address} port {port}"
