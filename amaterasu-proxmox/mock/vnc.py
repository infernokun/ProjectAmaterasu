"""A text console for mock VMs, spoken over RFB 3.8 the way a node's vncwebsocket does: VNC
authentication with the vncproxy ticket as the password, then raw framebuffer updates."""

import base64
import json
import os
import re
import secrets
import struct
import time
from pathlib import Path

from aiohttp import WSMsgType, web
from machines import World
from shell import CLEAR, ConsoleLogin

FONT = json.loads((Path(__file__).parent / "data" / "font.json").read_text())
FONT_W = FONT["width"]
FONT_H = FONT["height"]

COLS = 80
ROWS = 25
WIDTH = COLS * FONT_W
HEIGHT = ROWS * FONT_H
FG = (200, 200, 200)
BG = (12, 12, 12)
TICKET_MS = int(os.getenv("MOCK_VNC_TICKET_MS", "30000"))
CURSOR = "█"
# The cursor is a bar on rows 12 and 13 of the cell
CURSOR_ROWS = [0x1FF if 12 <= y < 14 else 0 for y in range(FONT_H)]

PATH = re.compile(r"^/api2/json/nodes/([^/]+)/qemu/(\d+)/vncwebsocket$")

_tickets: dict[str, dict] = {}
_issued_count = 0


def now_ms() -> int:
    return int(time.time() * 1000)


def issue_ticket(node: str, vm: dict, user: str = "root@pam") -> dict:
    """What POST .../vncproxy returns, for the API token's user (USER@REALM!TOKENID)."""
    global _issued_count
    port = str(5900 + (_issued_count % 100))
    _issued_count += 1
    ticket = f"PVEVNC:{now_ms():X}::{base64.b64encode(secrets.token_bytes(24)).decode()}"
    _tickets[ticket] = {"node": node, "vmid": vm["vmid"], "port": port, "expires": now_ms() + TICKET_MS}
    return {
        "cert": "-----BEGIN CERTIFICATE-----\nMOCK\n-----END CERTIFICATE-----\n",
        "port": port,
        "ticket": ticket,
        "upid": f"UPID:{node}:{now_ms():08X}:00000000:{int(time.time()):08X}:vncproxy:{vm['vmid']}:{user}:",
        "user": user,
    }


def console_handler(world: World):
    """Serves /api2/json/nodes/{node}/qemu/{vmid}/vncwebsocket."""

    async def handle(request: web.Request) -> web.StreamResponse:
        match = PATH.match(request.path)
        ws = web.WebSocketResponse(protocols=("binary",), compress=False)
        if not match or not ws.can_prepare(request).ok:
            print(f"Unhandled request: {request.method} {request.path}")
            return web.Response(status=501, text="API endpoint not implemented in mock", content_type="text/html")
        if not request.headers.get("Authorization", "").startswith("PVEAPIToken"):
            return web.Response(status=401, reason="No ticket")
        node, vmid = match[1], int(match[2])
        ticket = request.query.get("vncticket")
        issued = _tickets.pop(ticket, None)
        if (not issued or issued["node"] != node or issued["vmid"] != vmid
                or issued["port"] != request.query.get("port") or issued["expires"] < now_ms()):
            print(f"Rejected console for VM {vmid}: bad or expired ticket")
            return web.Response(status=401, reason="Permission check failed")
        machine = world.machine(vmid)
        if not machine or not machine.running:
            return web.Response(status=500, reason=f"VM {vmid} not running")

        await ws.prepare(request)
        print(f"Console opened for VM {vmid} ({machine.hostname})")
        await Console(ws, machine).serve()
        return ws

    return handle


class ProtocolError(Exception):
    pass


class Console:
    def __init__(self, ws: web.WebSocketResponse, machine):
        self.ws = ws
        self.machine = machine
        self.vm = machine.vm
        self.session = ConsoleLogin(machine)
        self.screen = ""  # what has been printed, the current prompt included
        self.input = ""
        self.ctrl = False
        self.write(self.session.banner() + self.session.prompt)
        self.format = {"big_endian": False, "rshift": 16, "gshift": 8, "bshift": 0, "rmax": 255, "gmax": 255, "bmax": 255}
        self.stage = "version"
        self.pending = bytearray()
        self.wants_frame = False
        self.dirty = True
        self.outbox: list[bytes] = []

    async def serve(self):
        await self.ws.send_bytes(b"RFB 003.008\n")
        async for msg in self.ws:
            if msg.type == WSMsgType.BINARY:
                self.pending += msg.data
            elif msg.type == WSMsgType.TEXT:
                self.pending += msg.data.encode()
            else:
                break
            if not self.machine.running:
                print(f"Console for VM {self.vm['vmid']} closed: the VM stopped")
                await self.ws.close(code=1000, message=b"VM stopped")
                return
            try:
                while self.step():
                    pass
            except ProtocolError as e:
                print(f"Console for VM {self.vm['vmid']} closed: {e}")
                await self.drain()
                await self.ws.close(code=1002, message=b"Protocol error")
                return
            await self.drain()

    async def drain(self):
        while self.outbox and not self.ws.closed:
            await self.ws.send_bytes(self.outbox.pop(0))

    def send(self, data: bytes):
        self.outbox.append(data)

    def take(self, count: int) -> bytes | None:
        if len(self.pending) < count:
            return None
        data = bytes(self.pending[:count])
        del self.pending[:count]
        return data

    def step(self) -> bool:
        if self.stage == "version":
            if not self.take(12):
                return False
            self.send(bytes([1, 2]))
            self.stage = "security"
            return True
        if self.stage == "security":
            chosen = self.take(1)
            if not chosen:
                return False
            if chosen[0] != 2:
                raise ProtocolError(f"unsupported security type {chosen[0]}")
            self.send(secrets.token_bytes(16))
            self.stage = "auth"
            return True
        if self.stage == "auth":
            # A node checks the response against the ticket; the mock already checked the ticket
            if not self.take(16):
                return False
            self.send(bytes([0, 0, 0, 0]))
            self.stage = "init"
            return True
        if self.stage == "init":
            if not self.take(1):
                return False
            name = f"VM {self.vm['vmid']} ({self.vm['name']})".encode()
            pixel_format = bytes([32, 24, 0, 1, 0, 255, 0, 255, 0, 255, 16, 8, 0, 0, 0, 0])
            self.send(struct.pack(">HH", WIDTH, HEIGHT) + pixel_format + struct.pack(">I", len(name)) + name)
            self.stage = "normal"
            return True
        return self.message()

    def message(self) -> bool:
        if not self.pending:
            return False
        kind = self.pending[0]
        if kind == 0:  # SetPixelFormat
            msg = self.take(20)
            if not msg:
                return False
            if msg[4] != 32:
                raise ProtocolError(f"only 32 bit pixels are supported, got {msg[4]}")
            rmax, gmax, bmax = struct.unpack_from(">HHH", msg, 8)
            self.format = {"big_endian": msg[6] != 0, "rmax": rmax, "gmax": gmax, "bmax": bmax,
                           "rshift": msg[14], "gshift": msg[15], "bshift": msg[16]}
            self.dirty = True
            return True
        if kind == 2:  # SetEncodings
            if len(self.pending) < 4:
                return False
            (count,) = struct.unpack_from(">H", self.pending, 2)
            return self.take(4 + 4 * count) is not None
        if kind == 3:  # FramebufferUpdateRequest
            msg = self.take(10)
            if not msg:
                return False
            if msg[1] == 0:
                self.dirty = True
            self.wants_frame = True
            self.flush()
            return True
        if kind == 4:  # KeyEvent
            msg = self.take(8)
            if not msg:
                return False
            self.key(struct.unpack_from(">I", msg, 4)[0], bool(msg[1]))
            return True
        if kind == 5:  # PointerEvent
            return self.take(6) is not None
        if kind == 6:  # ClientCutText
            if len(self.pending) < 8:
                return False
            (length,) = struct.unpack_from(">I", self.pending, 4)
            return self.take(8 + length) is not None
        raise ProtocolError(f"unknown client message {kind}")

    def write(self, text: str):
        if CLEAR in text:
            self.screen = ""
            text = text.rsplit(CLEAR, 1)[1]
        # Keep a few screens of scrollback
        self.screen = (self.screen + text)[-COLS * ROWS * 4:]

    def key(self, keysym: int, down: bool = True):
        if keysym in (0xFFE3, 0xFFE4):  # Control_L, Control_R
            self.ctrl = down
            return
        if not down:
            return
        session = self.session
        if self.ctrl and keysym in (ord("c"), ord("C")):
            self.write((self.input if session.echo else "") + session.interrupt() + session.prompt)
            self.input = ""
        elif self.ctrl and keysym in (ord("l"), ord("L")):
            self.screen = ""
            self.write(session.prompt)
        elif self.ctrl and keysym in (ord("d"), ord("D")):
            if self.input or session.shell is None:  # at the login prompt Ctrl-D does nothing
                return
            self.enter("exit")
        elif 0x20 <= keysym <= 0x7E:
            self.input += chr(keysym)
        elif keysym == 0xFF08:
            self.input = self.input[:-1]
        elif keysym in (0xFF0D, 0xFF8D):
            self.enter(self.input)
        else:
            return
        self.dirty = True
        self.flush()

    def enter(self, line: str):
        echoed = line if self.session.echo else ""
        self.input = ""
        self.write(echoed + "\n")
        self.write(self.session.feed(line))
        self.write(self.session.prompt)
        self.dirty = True

    def rows(self) -> list[str]:
        """The screen's last ROWS rows, long lines wrapped, with the typed input after the prompt."""
        text = self.screen + (self.input if self.session.echo else "")
        rows = []
        for line in text.split("\n"):
            rows += [line[i:i + COLS] for i in range(0, len(line), COLS)] or [""]
        if rows and len(rows[-1]) == COLS:
            rows.append("")
        return rows[-ROWS:]

    def flush(self):
        if not self.wants_frame or not self.dirty:
            return
        self.wants_frame = False
        self.dirty = False
        # One raw rectangle covering the whole screen
        header = struct.pack(">BxH", 0, 1) + struct.pack(">HHHHi", 0, 0, WIDTH, HEIGHT, 0)
        self.send(header + self.render())

    def pixel(self, rgb: tuple[int, int, int]) -> bytes:
        f = self.format
        value = ((int(rgb[0] * f["rmax"] / 255) << f["rshift"])
                 | (int(rgb[1] * f["gmax"] / 255) << f["gshift"])
                 | (int(rgb[2] * f["bmax"] / 255) << f["bshift"])) & 0xFFFFFFFF
        return struct.pack(">I" if f["big_endian"] else "<I", value)

    def render(self) -> bytes:
        fg = self.pixel(FG)
        bg = self.pixel(BG)
        out = bytearray(bg * (WIDTH * HEIGHT))
        lines = self.rows()
        cursor_row = len(lines) - 1
        cell_rows: dict[int, bytes] = {}  # glyph row bits -> the 9 pixels it draws

        def cell_row(bits: int) -> bytes:
            if bits not in cell_rows:
                cell_rows[bits] = b"".join(fg if bits & (1 << (FONT_W - 1 - x)) else bg for x in range(FONT_W))
            return cell_rows[bits]

        for row, line in enumerate(lines):
            text = line + CURSOR if row == cursor_row else line
            for col, char in enumerate(text[:COLS]):
                glyph = CURSOR_ROWS if char == CURSOR else FONT["glyphs"].get(char)
                if not glyph:
                    continue
                for y, bits in enumerate(glyph):
                    if bits:
                        start = ((row * FONT_H + y) * WIDTH + col * FONT_W) * 4
                        out[start:start + FONT_W * 4] = cell_row(bits)
        return bytes(out)
