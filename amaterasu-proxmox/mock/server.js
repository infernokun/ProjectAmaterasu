const http = require("http");
const express = require("express");
const app = express();

const hostname = "127.0.0.1";
const port = 8006;

const version = require("./data/version.json");

app.get("/api2/json/version", (req, res) => {
  res.json(version);
  console.log(req.headers);
  res.status(200);
});

app.get("/", (req, res) => {
  res.send("Hello World!");
});

app.all("/{*any}", (req, res, next) => {
  res.send("Not implemented");
  res.status(204);
});

app.listen(port, () => {
  console.log(`Server running at http://${hostname}:${port}/`);
});
