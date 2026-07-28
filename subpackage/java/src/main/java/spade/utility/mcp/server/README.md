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
├── connection/             connections to the SPADE core kernel
│   ├── Context.java          bundles a query and control connection for tool handlers
│   ├── SPADEQuery.java         query-port connection
│   └── SPADEControl.java       control-port connection
├── setting/                this server's settings, built on spade.utility.setting
│   ├── Setting.java           the parsed result, grouped into Spade and MCP
│   ├── ServerMode.java         stdio|http
│   └── Parser.java             field-level parsing/validation
└── tool/                   the MCP tools exposed to clients
    ├── Registry.java          collects the available tools
    ├── Tool.java                tool interface
    ├── doc/                     tool documentation
    ├── query/                   query tools
    └── storage/                 storage management tools
```

## Testing

See [the test README](../../../../../../test/java/spade/utility/mcp/server/README.md) for what functionality is tested where.
