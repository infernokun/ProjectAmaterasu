const https = require("https");
const fs = require("fs");
const path = require("path");
const express = require("express");
const bodyParser = require("body-parser");
const app = express();

const hostname = "127.0.0.1";
const port = 8006;

const sslOptions = {
  key: fs.readFileSync(path.join(__dirname, "ssl", "key.pem")),
  cert: fs.readFileSync(path.join(__dirname, "ssl", "cert.pem")),
};

app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));

const version = require("./data/version.json");
const nodes = require("./data/nodes.json");
const nodeDetails = require("./data/nodeDetails.json");
const vms = require("./data/vms.json");
const vmDetails = require("./data/vmDetails.json");
const storage = require("./data/storage.json");
const tickets = require("./data/tickets.json");
const networkInterfaces = require("./data/networkInterfaces.json");
const vmConfigs = require("./data/vmConfigs.json");

// Version endpoint
app.get("/api2/json/version", (req, res) => {
  console.log("Version request headers:", req.headers);
  res.json(version);
});

// Authentication endpoint
app.post("/api2/json/access/ticket", (req, res) => {
  console.log("Auth request:", req.body);
  res.json(tickets);
});

// Nodes listing
app.get("/api2/json/nodes", (req, res) => {
  console.log("Nodes request headers:", req.headers);
  res.json(nodes);
});

// Node details
app.get("/api2/json/nodes/:node", (req, res) => {
  const node = req.params.node;
  console.log(`Node ${node} details request`);

  // You can customize the response based on the node parameter
  const customResponse = JSON.parse(JSON.stringify(nodeDetails));
  customResponse.data.node = node;

  res.json(customResponse);
});

// VMs listing for a node
app.get("/api2/json/nodes/:node/qemu", (req, res) => {
  const requestedNode = req.params.node;
  console.log(`${req.originalUrl}: VMs for node ${requestedNode} request`);

  const customResponse = JSON.parse(JSON.stringify(vms));

  if (customResponse.data && Array.isArray(customResponse.data)) {
    customResponse.data = customResponse.data.filter((vm) => {
      if (vm.node === requestedNode) {
        return true;
      }
      return false;
    });
  }

  console.log(
    `Sending ${customResponse.data.length} VMs for node ${requestedNode}`
  );
  res.status(200).json(customResponse);
});

// VM details
app.get("/api2/json/nodes/:node/qemu/:vmid/status/current", (req, res) => {
  const { node, vmid } = req.params;
  console.log(`VM ${vmid} on node ${node} details request`);

  // Customize response based on parameters
  const customResponse = JSON.parse(JSON.stringify(vmDetails));
  customResponse.data.node = node;
  customResponse.data.vmid = parseInt(vmid);

  res.json(customResponse);
});


app.get("/api2/json/nodes/:node/qemu/:vmid/config", (req, res) => {
  const { node, vmid } = req.params;
  console.log(`CONFIG get ${node} for ${vmid} requested`);

  const vmConfig = vmConfigs.find(v => v.vmid == vmid);

  res.json(JSON.parse(JSON.stringify(vmConfig)));
});

// Network interfaces for a node
app.get("/api2/json/nodes/:node/network", (req, res) => {
  const { node } = req.params;
  console.log(`Network interfaces for node ${node} requested`);


  res.json(JSON.parse(JSON.stringify(networkInterfaces)));
});

app.post("/api2/json/nodes/:node/network", (req, res) => {
  const { node } = req.params;
  console.log(`Network interfaces POST node ${node} requested`);

  res.status(200).send();
});


app.put("/api2/json/nodes/:node/network", (req, res) => {
  const { node } = req.params;
  console.log(`Network interfaces PUT node ${node} requested`);

  res.status(200).send();
});

app.post("/api2/json/nodes/:node/qemu/:vmid/clone", (req, res) => {
  const { node, vmid } = req.params;
  console.log(`CLONE post ${node} for ${vmid} requested`);

  res.status(200).send();
});

app.post("/api2/json/nodes/:node/qemu/:vmid/status/start", (req, res) => {
  const { node, vmid } = req.params;
  console.log(`POST start ${node} for ${vmid} requested`);

  res.status(200).send();
});

app.post("/api2/json/nodes/:node/qemu/:vmid/status/stop", (req, res) => {
  const { node, vmid } = req.params;
  console.log(`POST stop ${node} for ${vmid} requested`);

  res.status(200).send();
});

app.delete("/api2/json/nodes/:node/qemu/:vmid", (req, res) => {
  const { node, vmid } = req.params;
  console.log(`DELETE delete ${node} for ${vmid} requested`);

  res.status(200).send();
});

// Storage endpoints
app.get("/api2/json/storage", (req, res) => {
  console.log("Storage request");
  res.json(storage);
});

// Root route
app.get("/", (req, res) => {
  res.send("Proxmox VE API Mock Server");
});

// Catch-all route - Fixed pattern
app.all("/{*any}", (req, res) => {
  console.log(`Unhandled request: ${req.method} ${req.path}`);
  res.status(501).send("API endpoint not implemented in mock");
});

// Create HTTPS server
const server = https.createServer(sslOptions, app);

// Start the server
server.listen(port, hostname, () => {
  console.log(
    `Proxmox API Mock HTTPS Server running at https://${hostname}:${port}/`
  );
});
