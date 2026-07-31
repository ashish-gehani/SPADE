# SPADE MCP Server

Exposes SPADE's query and control capabilities as tools consumable by MCP-compatible clients, over HTTP or stdio transports. See [docs/mcp/HOW-TO.md](../../../../../../../../../docs/mcp/HOW-TO.md) for setup and usage (starting SPADE, configuring a transport, connecting a client).

## Layout

```
server/
├── Main.java              entry point: parses settings, then starts the chosen transport
├── Server.java             abstract base shared by both transports (owns the parsed Setting, State, tool Registry)
├── State.java              running/shutdown state
├── Http.java               HTTP transport
├── Stdio.java              stdio transport
├── setting/                this server's settings, built on spade.utility.setting
│   ├── Setting.java           the parsed result (registry config path + MCP transport config)
│   ├── ServerMode.java         stdio|http
│   └── Parser.java             field-level parsing/validation
└── tool/                   the MCP tools exposed to clients; see [tool/README.md](tool/README.md)
```

## Testing

See [the test README](../../../../../../test/java/spade/utility/mcp/server/README.md) for what functionality is tested where.
