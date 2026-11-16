"""End-to-end check of the mock: deploys a bastion lab through the API the way the REST service does, then logs in
over SSH (a command on the bastion, a hop to the target, a forward to its web server) and on the VNC console.

    python mock/selftest.py

Starts its own mock on MOCK_PORT (default 18006) with SSH on 127.0.0.1:MOCK_SSH_PORT (default 12022)."""

import asyncio
import os
import ssl
import struct
import subprocess
import sys
from pathlib import Path

import aiohttp
import asyncssh

HERE = Path(__file__).parent
PORT = int(os.getenv("MOCK_PORT", "18006"))
SSH_PORT = int(os.getenv("MOCK_SSH_PORT", "12022"))
API = f"https://127.0.0.1:{PORT}/api2/json"
NODE = f"{API}/nodes/inferno"
TOKEN = {"Authorization": "PVEAPIToken=root@pam!selftest=secret"}
PASSWORD = "selftest-pass-1234"
checks = 0


def check(condition: bool, what: str) -> None:
    global checks
    if not condition:
        raise SystemExit(f"FAIL: {what}")
    checks += 1
    print(f"ok  {what}")


async def deploy(s: aiohttp.ClientSession) -> None:
    async def call(method: str, path: str, body: dict | None = None):
        async with s.request(method, NODE + path, json=body) as r:
            data = await r.json()
            assert r.status == 200, (method, path, r.status, data)
            return data["data"]

    async def wait(upid: str):
        for _ in range(100):
            task = await call("GET", f"/tasks/{upid}/status")
            if task["status"] == "stopped":
                assert task["exitstatus"] == "OK", task
                return
            await asyncio.sleep(0.1)
        raise AssertionError(f"task {upid} did not finish")

    await call("POST", "/network", {"iface": "abr0", "type": "bridge", "autostart": 1})
    await wait(await call("PUT", "/network", {}))
    await wait(await call("POST", "/qemu/9005/clone", {"newid": 200, "name": "Team-Lab-tpl-bastion"}))
    await wait(await call("POST", "/qemu/9004/clone", {"newid": 201, "name": "Team-Lab-target"}))
    await call("PUT", "/qemu/200/config", {"net0": "virtio,bridge=vmbr0", "net1": "virtio,bridge=abr0", "agent": "1"})
    await call("PUT", "/qemu/200/config", {"cipassword": PASSWORD})
    await call("PUT", "/qemu/201/config", {"net0": "virtio,bridge=abr0", "agent": "1"})
    for vmid in (200, 201):
        await wait(await call("POST", f"/qemu/{vmid}/status/start", {}))
    config = await call("GET", "/qemu/9005/config")
    check(f"ssh-port-{SSH_PORT}" in config["tags"], "the bastion template carries its SSH port as a tag")
    await asyncio.sleep(0.5)  # MOCK_AGENT_MS
    agent = await call("GET", "/qemu/200/agent/network-get-interfaces")
    addresses = [a["ip-address"] for i in agent["result"] for a in i["ip-addresses"] if a["ip-address-type"] == "ipv4"]
    check("127.0.0.1" in addresses and "172.16.0.1" in addresses, "the bastion's guest agent reports its uplink and lab addresses")


async def over_ssh() -> None:
    options = {"port": SSH_PORT, "known_hosts": None}
    try:
        await asyncssh.connect("127.0.0.1", username="player", password="wrong", **options)
        check(False, "a wrong password is refused")
    except asyncssh.PermissionDenied:
        check(True, "a wrong password is refused")

    async with asyncssh.connect("127.0.0.1", username="player", password=PASSWORD, **options) as bastion:
        result = await bastion.run("hostname; whoami; sysctl net.ipv4.ip_forward; nmap -sn 172.16.0.0/24")
        check("Team-Lab-tpl-bastion\nplayer\n" in result.stdout, "a command runs on the bastion as its cloud-init user")
        check("net.ipv4.ip_forward = 0" in result.stdout, "the bastion does not forward")
        check("172.16.0.20" in result.stdout, "nmap on the bastion finds the target")

        async with asyncssh.connect("172.16.0.20", username="root", password="alpine", tunnel=bastion,
                                    known_hosts=None) as target:
            result = await target.run("hostname; cat /root/flag.txt")
            check(result.stdout.startswith("Team-Lab-target\nFLAG{"), "ssh -J reaches the target and its root flag")

        reader, writer = await bastion.open_connection("172.16.0.20", 80)
        writer.write(b"GET / HTTP/1.1\r\nHost: target\r\n\r\n")
        page = (await reader.read()).decode()
        check(page.startswith("HTTP/1.1 200 OK") and "Team-Lab-target" in page, "ssh -L reaches the target's web server")

        try:
            await bastion.open_connection("172.16.9.9", 22)
            check(False, "an address outside the lab network is unreachable")
        except asyncssh.ChannelOpenError:
            check(True, "an address outside the lab network is unreachable")

        process = await bastion.create_process(term_type="xterm")
        process.stdin.write("ssh root@172.16.0.20\n")
        await asyncio.sleep(0.3)
        process.stdin.write("alpine\n")
        await asyncio.sleep(0.3)
        process.stdin.write("whoami\nexit\nexit\n")
        output = (await asyncio.wait_for(process.wait(), 10)).stdout
        check("root@172.16.0.20's password:" in output and "\r\nroot\r\n" in output
              and "Connection to 172.16.0.20 closed." in output, "an interactive ssh hop from the bastion's shell")


async def over_vnc(s: aiohttp.ClientSession) -> None:
    async with s.post(f"{NODE}/qemu/201/vncproxy", json={"websocket": 1}) as r:
        ticket = (await r.json())["data"]
    ws = await s.ws_connect(f"{NODE}/qemu/201/vncwebsocket", protocols=("binary",),
                            params={"port": ticket["port"], "vncticket": ticket["ticket"]})
    buffer = bytearray()

    async def read(n: int) -> bytes:
        while len(buffer) < n:
            buffer.extend((await asyncio.wait_for(ws.receive(), 5)).data)
        data = bytes(buffer[:n])
        del buffer[:n]
        return data

    async def frame() -> bytes:
        await ws.send_bytes(b"\x03\x01" + struct.pack(">HHHH", 0, 0, 720, 400))
        header = await read(16)
        width, height = struct.unpack_from(">HH", header, 8)
        return await read(width * height * 4)

    check(await read(12) == b"RFB 003.008\n", "the console speaks RFB 3.8")
    await ws.send_bytes(b"RFB 003.008\n")
    await read(2)
    await ws.send_bytes(b"\x02")
    await read(16)
    await ws.send_bytes(bytes(16))
    check(await read(4) == b"\x00\x00\x00\x00", "the console accepts the ticket")
    await ws.send_bytes(b"\x01")
    init = await read(24)
    await read(struct.unpack_from(">I", init, 20)[0])
    before = await frame()
    for ch in "root\nalpine\n":
        key = 0xFF0D if ch == "\n" else ord(ch)
        await ws.send_bytes(b"\x04\x01\x00\x00" + struct.pack(">I", key))
    after = await frame()
    check(before != after, "typing a login on the console changes the screen")
    await ws.close()


async def main() -> None:
    env = {**os.environ, "MOCK_PORT": str(PORT), "MOCK_TASK_MS": "100", "MOCK_AGENT_MS": "200",
           "MOCK_SSH_ADDRESS": "127.0.0.1", "MOCK_SSH_PORT": str(SSH_PORT)}
    mock = subprocess.Popen([sys.executable, str(HERE / "server.py")], env=env)
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    try:
        async with aiohttp.ClientSession(connector=aiohttp.TCPConnector(ssl=ctx), headers=TOKEN) as s:
            for _ in range(50):
                try:
                    async with s.get(f"{API}/version") as r:
                        if r.status == 200:
                            break
                except aiohttp.ClientError:
                    pass
                await asyncio.sleep(0.2)
            await deploy(s)
            await over_ssh()
            await over_vnc(s)
    finally:
        mock.terminate()
        mock.wait()
    print(f"{checks} checks passed")


if __name__ == "__main__":
    asyncio.run(main())
