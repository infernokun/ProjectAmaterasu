const crypto = require("crypto");
const { WebSocketServer } = require("ws");
const font = require("./data/font.json");

// A text console for mock VMs, spoken over RFB 3.8 the way a node's vncwebsocket does: VNC
// authentication with the vncproxy ticket as the password, then raw framebuffer updates.

const COLS = 80;
const ROWS = 25;
const WIDTH = COLS * font.width;
const HEIGHT = ROWS * font.height;
const FG = [200, 200, 200];
const BG = [12, 12, 12];
const TICKET_MS = Number(process.env.MOCK_VNC_TICKET_MS || 30000);

const tickets = new Map();
let issuedCount = 0;

/** What POST .../vncproxy returns. */
function issueTicket(node, vm) {
  const port = String(5900 + (issuedCount++ % 100));
  const ticket = `PVEVNC:${Date.now().toString(16).toUpperCase()}::${crypto.randomBytes(24).toString("base64")}`;
  tickets.set(ticket, { node, vmid: vm.vmid, port, expires: Date.now() + TICKET_MS });
  return {
    cert: "-----BEGIN CERTIFICATE-----\nMOCK\n-----END CERTIFICATE-----\n",
    port,
    ticket,
    upid: `UPID:${node}:${Date.now().toString(16)}:0:vncproxy:${vm.vmid}:root@pam:`,
    user: "root@pam",
  };
}

function reject(socket, status, message) {
  socket.write(`HTTP/1.1 ${status} ${message}\r\nConnection: close\r\nContent-Length: 0\r\n\r\n`);
  socket.destroy();
}

/** Serves /api2/json/nodes/:node/qemu/:vmid/vncwebsocket on the HTTPS server. */
function attachConsoles(server, vms) {
  const wss = new WebSocketServer({
    noServer: true,
    handleProtocols: (protocols) => (protocols.has("binary") ? "binary" : false),
  });

  server.on("upgrade", (req, socket, head) => {
    const url = new URL(req.url, "https://mock");
    const match = /^\/api2\/json\/nodes\/([^/]+)\/qemu\/(\d+)\/vncwebsocket$/.exec(url.pathname);
    if (!match) return reject(socket, 404, "Not Found");
    if (!String(req.headers.authorization || "").startsWith("PVEAPIToken")) {
      return reject(socket, 401, "No ticket");
    }
    const [, node, vmidText] = match;
    const vmid = Number(vmidText);
    const ticket = url.searchParams.get("vncticket");
    const issued = tickets.get(ticket);
    tickets.delete(ticket);
    if (!issued || issued.node !== node || issued.vmid !== vmid || issued.port !== url.searchParams.get("port")
      || issued.expires < Date.now()) {
      console.log(`Rejected console for VM ${vmid}: bad or expired ticket`);
      return reject(socket, 401, "Permission check failed");
    }
    const vm = vms.get(vmid);
    if (!vm || vm.status !== "running") return reject(socket, 500, `VM ${vmid} not running`);

    wss.handleUpgrade(req, socket, head, (ws) => {
      console.log(`Console opened for VM ${vmid} (${vm.name})`);
      serveConsole(ws, vm);
    });
  });
}

function serveConsole(ws, vm) {
  const lines = [
    `Debian GNU/Linux 12 ${vm.name} tty1`,
    "",
    `${vm.name} login: `,
  ];
  let input = "";
  let promptLength = lines[2].length;
  let awaitingPassword = false;
  let format = { bigEndian: false, rshift: 16, gshift: 8, bshift: 0, rmax: 255, gmax: 255, bmax: 255 };
  let stage = "version";
  let pending = Buffer.alloc(0);
  let wantsFrame = false;
  let dirty = true;

  const send = (bytes) => {
    if (ws.readyState === ws.OPEN) ws.send(bytes);
  };

  send(Buffer.from("RFB 003.008\n"));

  ws.on("message", (data) => {
    pending = Buffer.concat([pending, Buffer.isBuffer(data) ? data : Buffer.from(data)]);
    try {
      while (step()) { /* keep parsing */ }
    } catch (e) {
      console.log(`Console for VM ${vm.vmid} closed: ${e.message}`);
      ws.close(1002, "Protocol error");
    }
  });

  function take(count) {
    if (pending.length < count) return null;
    const bytes = pending.subarray(0, count);
    pending = pending.subarray(count);
    return bytes;
  }

  function step() {
    switch (stage) {
      case "version":
        if (!take(12)) return false;
        send(Buffer.from([1, 2]));
        stage = "security";
        return true;
      case "security": {
        const chosen = take(1);
        if (!chosen) return false;
        if (chosen[0] !== 2) throw new Error(`unsupported security type ${chosen[0]}`);
        send(crypto.randomBytes(16));
        stage = "auth";
        return true;
      }
      case "auth":
        // A node checks the response against the ticket; the mock already checked the ticket
        if (!take(16)) return false;
        send(Buffer.from([0, 0, 0, 0]));
        stage = "init";
        return true;
      case "init": {
        if (!take(1)) return false;
        const name = Buffer.from(`VM ${vm.vmid} (${vm.name})`);
        const init = Buffer.alloc(24 + name.length);
        init.writeUInt16BE(WIDTH, 0);
        init.writeUInt16BE(HEIGHT, 2);
        Buffer.from([32, 24, 0, 1, 0, 255, 0, 255, 0, 255, 16, 8, 0, 0, 0, 0]).copy(init, 4);
        init.writeUInt32BE(name.length, 20);
        name.copy(init, 24);
        send(init);
        stage = "normal";
        return true;
      }
      default:
        return message();
    }
  }

  function message() {
    if (pending.length < 1) return false;
    switch (pending[0]) {
      case 0: {
        const msg = take(20);
        if (!msg) return false;
        if (msg[4] !== 32) throw new Error(`only 32 bit pixels are supported, got ${msg[4]}`);
        format = {
          bigEndian: msg[6] !== 0,
          rmax: msg.readUInt16BE(8), gmax: msg.readUInt16BE(10), bmax: msg.readUInt16BE(12),
          rshift: msg[14], gshift: msg[15], bshift: msg[16],
        };
        dirty = true;
        return true;
      }
      case 2: {
        if (pending.length < 4) return false;
        return !!take(4 + 4 * pending.readUInt16BE(2));
      }
      case 3: {
        const msg = take(10);
        if (!msg) return false;
        if (msg[1] === 0) dirty = true;
        wantsFrame = true;
        flush();
        return true;
      }
      case 4: {
        const msg = take(8);
        if (!msg) return false;
        if (msg[1]) key(msg.readUInt32BE(4));
        return true;
      }
      case 5:
        return !!take(6);
      case 6: {
        if (pending.length < 8) return false;
        return !!take(8 + pending.readUInt32BE(4));
      }
      default:
        throw new Error(`unknown client message ${pending[0]}`);
    }
  }

  function key(keysym) {
    const last = lines.length - 1;
    if (keysym >= 0x20 && keysym <= 0x7e) {
      input += String.fromCharCode(keysym);
      if (!awaitingPassword) lines[last] += String.fromCharCode(keysym);
    } else if (keysym === 0xff08 && input.length) {
      input = input.slice(0, -1);
      if (!awaitingPassword && lines[last].length > promptLength) lines[last] = lines[last].slice(0, -1);
    } else if (keysym === 0xff0d || keysym === 0xff8d) {
      if (awaitingPassword) {
        lines.push("", "Login incorrect", `${vm.name} login: `);
        awaitingPassword = false;
      } else if (input.length) {
        lines.push("Password: ");
        awaitingPassword = true;
      } else {
        lines.push(`${vm.name} login: `);
      }
      promptLength = lines[lines.length - 1].length;
      input = "";
      while (lines.length > ROWS) lines.shift();
    } else {
      return;
    }
    dirty = true;
    flush();
  }

  function flush() {
    if (!wantsFrame || !dirty) return;
    wantsFrame = false;
    dirty = false;
    const header = Buffer.alloc(16);
    header.writeUInt8(0, 0);
    header.writeUInt16BE(1, 2);
    header.writeUInt16BE(WIDTH, 8);
    header.writeUInt16BE(HEIGHT, 10);
    header.writeInt32BE(0, 12);
    send(Buffer.concat([header, render()]));
  }

  function pixel(rgb) {
    return (((rgb[0] * format.rmax / 255) | 0) << format.rshift
      | ((rgb[1] * format.gmax / 255) | 0) << format.gshift
      | ((rgb[2] * format.bmax / 255) | 0) << format.bshift) >>> 0;
  }

  function render() {
    const out = Buffer.alloc(WIDTH * HEIGHT * 4);
    const fg = pixel(FG);
    const bg = pixel(BG);
    const write = format.bigEndian ? (v, o) => out.writeUInt32BE(v, o) : (v, o) => out.writeUInt32LE(v, o);
    for (let i = 0; i < WIDTH * HEIGHT; i++) write(bg, i * 4);
    const cursorRow = lines.length - 1;
    lines.forEach((line, row) => {
      const text = row === cursorRow ? line + "█" : line;
      for (let col = 0; col < Math.min(COLS, text.length); col++) {
        const glyph = text[col] === "█" ? null : font.glyphs[text[col]];
        for (let y = 0; y < font.height; y++) {
          const bits = glyph ? glyph[y] : (y >= 12 && y < 14 ? 0x1ff : 0);
          for (let x = 0; x < font.width; x++) {
            if (bits & (1 << (font.width - 1 - x))) {
              write(fg, ((row * font.height + y) * WIDTH + col * font.width + x) * 4);
            }
          }
        }
      }
    });
    return out;
  }
}

module.exports = { attachConsoles, issueTicket };
