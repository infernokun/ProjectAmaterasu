const https = require("https");
const fs = require("fs");
const path = require("path");
const express = require("express");
const bodyParser = require("body-parser");
const { attachConsoles, issueTicket } = require("./vnc");

// Stateful Proxmox VE API mock: long running calls return a task UPID that finishes
// after TASK_MS, clones stay locked until then, and running VMs cannot be destroyed.

const hostname = process.env.MOCK_HOST || "127.0.0.1";
const port = Number(process.env.MOCK_PORT || 8006);
const TASK_MS = Number(process.env.MOCK_TASK_MS || 3000);

const sslOptions = {
  key: fs.readFileSync(path.join(__dirname, "ssl", "key.pem")),
  cert: fs.readFileSync(path.join(__dirname, "ssl", "cert.pem")),
};

const app = express();
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));

const version = require("./data/version.json");
const nodes = require("./data/nodes.json");
const nodeDetails = require("./data/nodeDetails.json");
const storage = require("./data/storage.json");
const tickets = require("./data/tickets.json");
// Answers from a PVE 9.2.2 node called proxmox2
const nodeStatus = require("./data/nodeStatus.json");
const permissions = require("./data/permissions.json");
const STARTED = Date.now();

const vms = new Map();
const configs = new Map();
const tasks = new Map();
let networks = JSON.parse(fs.readFileSync(path.join(__dirname, "data", "networkInterfaces.json"))).data;
let pendingNetworks = null;

function randomMac() {
  const hex = () => Math.floor(Math.random() * 256).toString(16).padStart(2, "0");
  return `52:54:00:${hex()}:${hex()}:${hex()}`;
}

const knownNodes = new Set(nodes.data.map((n) => n.node));
for (const vm of require("./data/vms.json").data) {
  knownNodes.add(vm.node);
  vms.set(vm.vmid, { ...vm, status: vm.template ? "stopped" : vm.status });
  const config = { name: vm.name, agent: "0" };
  config.net0 = `virtio=${randomMac()},bridge=vmbr0,firewall=1`;
  if (vm.vmid === 9000 || vm.vmid === 9002) {
    config.net1 = `virtio=${randomMac()},bridge=vmbr1,firewall=1`;
  }
  if (vm.vmid === 9001) {
    config.net0 = `virtio=${randomMac()},bridge=vmbr1,firewall=1`;
  }
  configs.set(vm.vmid, config);
}

function error(res, status, message) {
  res.status(status).json({ data: null, message, errors: { message } });
}

function startTask(node, type, vmid, action) {
  const upid = `UPID:${node}:${Date.now().toString(16)}:${Math.floor(Math.random() * 1e6)}:${type}:${vmid}:root@pam:`;
  tasks.set(upid, { status: "running", exitstatus: null, type, id: String(vmid) });
  setTimeout(() => {
    let exitstatus = "OK";
    try {
      action();
    } catch (e) {
      exitstatus = e.message;
    }
    tasks.set(upid, { status: "stopped", exitstatus, type, id: String(vmid) });
  }, TASK_MS);
  return upid;
}

function getVm(req, res) {
  const vmid = Number(req.params.vmid);
  const vm = vms.get(vmid);
  if (!vm || vm.node !== req.params.node) {
    error(res, 500, `Configuration file 'nodes/${req.params.node}/qemu-server/${vmid}.conf' does not exist`);
    return null;
  }
  return vm;
}

function requireUnlocked(vm, res) {
  if (vm.lock) {
    error(res, 500, `VM is locked (${vm.lock})`);
    return false;
  }
  return true;
}

app.get("/api2/json/version", (req, res) => res.json(version));
app.post("/api2/json/access/ticket", (req, res) => res.json(tickets));
app.get("/api2/json/nodes", (req, res) => res.json(nodes));
app.get("/api2/json/storage", (req, res) => res.json(storage));

app.get("/api2/json/nodes/:node", (req, res) => {
  const response = JSON.parse(JSON.stringify(nodeDetails));
  response.data.node = req.params.node;
  res.json(response);
});

app.get("/api2/json/nodes/:node/status", (req, res) => {
  const response = JSON.parse(JSON.stringify(nodeStatus));
  response.data.uptime += Math.floor((Date.now() - STARTED) / 1000);
  res.json(response);
});

// The token's privileges per path; the recorded node's entry stands for every node
app.get("/api2/json/access/permissions", (req, res) => {
  const nodePrivileges = permissions.data["/nodes/proxmox2"];
  const all = Object.fromEntries(Object.entries(permissions.data).filter(([path]) => path !== "/nodes/proxmox2"));
  for (const node of knownNodes) all[`/nodes/${node}`] = nodePrivileges;
  const path = req.query.path;
  if (!path) return res.json({ data: all });
  res.json({ data: { [path]: all[path] || (path.startsWith("/nodes/") ? nodePrivileges : {}) } });
});

app.get("/api2/json/cluster/nextid", (req, res) => {
  let id = 100;
  while (vms.has(id)) id++;
  res.json({ data: String(id) });
});

app.get("/api2/json/nodes/:node/tasks/:upid/status", (req, res) => {
  const task = tasks.get(req.params.upid);
  if (!task) return error(res, 500, "no such task");
  res.json({ data: { upid: req.params.upid, node: req.params.node, ...task } });
});

app.get("/api2/json/nodes/:node/qemu", (req, res) => {
  res.json({ data: [...vms.values()].filter((vm) => vm.node === req.params.node) });
});

app.get("/api2/json/nodes/:node/qemu/:vmid/status/current", (req, res) => {
  const vm = getVm(req, res);
  if (vm) res.json({ data: vm });
});

app.get("/api2/json/nodes/:node/qemu/:vmid/config", (req, res) => {
  const vm = getVm(req, res);
  if (vm) res.json({ data: { ...configs.get(vm.vmid), ...(vm.lock ? { lock: vm.lock } : {}) } });
});

function updateConfig(req, res) {
  const vm = getVm(req, res);
  if (!vm || !requireUnlocked(vm, res)) return;
  const config = configs.get(vm.vmid);
  const { delete: toDelete, node, ...changes } = req.body || {};
  for (const [key, value] of Object.entries(changes)) {
    if (/^net\d+$/.test(key)) {
      const bridge = /bridge=([^,]+)/.exec(String(value));
      const known = networks.some((n) => n.iface === (bridge && bridge[1]));
      if (bridge && !known) return error(res, 400, `bridge '${bridge[1]}' does not exist`);
    }
    config[key] = String(value);
  }
  if (toDelete) String(toDelete).split(",").forEach((key) => delete config[key.trim()]);
  res.json({ data: null });
}
app.post("/api2/json/nodes/:node/qemu/:vmid/config", updateConfig);
app.put("/api2/json/nodes/:node/qemu/:vmid/config", updateConfig);

app.post("/api2/json/nodes/:node/qemu/:vmid/clone", (req, res) => {
  const source = getVm(req, res);
  if (!source || !requireUnlocked(source, res)) return;
  const newid = Number(req.body.newid);
  if (!newid || vms.has(newid)) return error(res, 500, `unable to create VM ${newid}: config file already exists`);

  const clone = {
    ...source,
    vmid: newid,
    name: req.body.name || `${source.name}-clone`,
    description: req.body.description,
    template: 0,
    status: "stopped",
    uptime: 0,
    lock: "clone",
  };
  vms.set(newid, clone);
  const config = { ...configs.get(source.vmid), name: clone.name };
  for (const key of Object.keys(config)) {
    if (/^net\d+$/.test(key)) config[key] = config[key].replace(/=([0-9A-Fa-f:]{17})/, `=${randomMac()}`);
  }
  configs.set(newid, config);

  res.json({ data: startTask(req.params.node, "qmclone", source.vmid, () => delete clone.lock) });
});

function statusAction(type, check, finalStatus) {
  return (req, res) => {
    const vm = getVm(req, res);
    if (!vm || !requireUnlocked(vm, res)) return;
    const problem = check(vm);
    if (problem) return error(res, 500, problem);
    res.json({
      data: startTask(req.params.node, type, vm.vmid, () => {
        vm.status = finalStatus;
        vm.uptime = finalStatus === "running" ? 1 : 0;
      }),
    });
  };
}

app.post("/api2/json/nodes/:node/qemu/:vmid/status/start",
  statusAction("qmstart", (vm) => (vm.template ? "you can't start a vm if it's a template" : null), "running"));
app.post("/api2/json/nodes/:node/qemu/:vmid/status/stop", statusAction("qmstop", () => null, "stopped"));
app.post("/api2/json/nodes/:node/qemu/:vmid/status/shutdown",
  statusAction("qmshutdown", (vm) => (vm.status !== "running" ? `VM ${vm.vmid} not running` : null), "stopped"));

app.delete("/api2/json/nodes/:node/qemu/:vmid", (req, res) => {
  const vm = getVm(req, res);
  if (!vm || !requireUnlocked(vm, res)) return;
  if (vm.status === "running") return error(res, 500, `VM ${vm.vmid} is running - destroy failed`);
  vm.lock = "destroyed";
  res.json({
    data: startTask(req.params.node, "qmdestroy", vm.vmid, () => {
      vms.delete(vm.vmid);
      configs.delete(vm.vmid);
    }),
  });
});

app.post("/api2/json/nodes/:node/qemu/:vmid/vncproxy", (req, res) => {
  const vm = getVm(req, res);
  if (!vm || !requireUnlocked(vm, res)) return;
  if (vm.template) return error(res, 500, "you can't open a console on a template");
  if (vm.status !== "running") return error(res, 500, `VM ${vm.vmid} not running`);
  res.json({ data: issueTicket(req.params.node, vm) });
});

function stanza(n) {
  const lines = n.autostart ? [`auto ${n.iface}`] : [];
  lines.push(`iface ${n.iface} inet ${n.method || "manual"}`);
  if (n.type === "bridge") lines.push(`\tbridge-ports ${n.bridge_ports || "none"}`, "\tbridge-stp off", "\tbridge-fd 0");
  if (n.comments) lines.push(`#${String(n.comments).trim()}`);
  return lines;
}

// Like PVE, "changes" is a diff of interfaces against interfaces.new, sent only while edits are pending
function pendingChanges() {
  if (!pendingNetworks) return null;
  const before = new Set(networks.map((n) => n.iface));
  const after = new Set(pendingNetworks.map((n) => n.iface));
  const hunks = [];
  for (const n of networks.filter((n) => !after.has(n.iface))) hunks.push("@@ -1,1 +1,0 @@", "-", ...stanza(n).map((l) => `-${l}`));
  for (const n of pendingNetworks.filter((n) => !before.has(n.iface))) hunks.push("@@ -1,0 +1,1 @@", "+", ...stanza(n).map((l) => `+${l}`));
  if (!hunks.length) return null;
  return ["--- /etc/network/interfaces", "+++ /etc/network/interfaces.new", ...hunks].join("\n") + "\n";
}

app.get("/api2/json/nodes/:node/network", (req, res) => {
  const changes = pendingChanges();
  res.json({ ...(changes ? { changes } : {}), data: pendingNetworks || networks });
});

app.post("/api2/json/nodes/:node/network", (req, res) => {
  const { iface, type } = req.body;
  const target = pendingNetworks || (pendingNetworks = JSON.parse(JSON.stringify(networks)));
  if (target.some((n) => n.iface === iface)) return error(res, 400, `interface '${iface}' already exists`);
  target.push({ iface, type, active: 0, autostart: Number(req.body.autostart || 0), comments: req.body.comments, families: [] });
  res.json({ data: null });
});

app.delete("/api2/json/nodes/:node/network/:iface", (req, res) => {
  const target = pendingNetworks || (pendingNetworks = JSON.parse(JSON.stringify(networks)));
  if (!target.some((n) => n.iface === req.params.iface)) return error(res, 400, `interface '${req.params.iface}' does not exist`);
  pendingNetworks = target.filter((n) => n.iface !== req.params.iface);
  res.json({ data: null });
});

app.put("/api2/json/nodes/:node/network", (req, res) => {
  if (pendingNetworks) {
    networks = pendingNetworks.map((n) => ({ ...n, active: 1 }));
    pendingNetworks = null;
  }
  res.json({ data: startTask(req.params.node, "srvreload", "networking", () => {}) });
});

app.get("/", (req, res) => res.send("Proxmox VE API Mock Server"));

app.all("/{*any}", (req, res) => {
  console.log(`Unhandled request: ${req.method} ${req.path}`);
  res.status(501).send("API endpoint not implemented in mock");
});

const server = https.createServer(sslOptions, app);
attachConsoles(server, vms);
server.listen(port, hostname, () => {
  console.log(`Proxmox API Mock HTTPS Server running at https://${hostname}:${port}/`);
});
