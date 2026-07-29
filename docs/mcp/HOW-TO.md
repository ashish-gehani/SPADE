# SPADE MCP Server

The SPADE MCP server exposes SPADE's query and control capabilities as tools consumable by MCP-compatible clients such as Claude Code and Claude Desktop. Two transports are supported: **HTTP** (for Claude Code and remote clients) and **stdio** (for Claude Desktop and local process-based clients).

---

## HTTP Transport (Claude Code)

### 1. Start SPADE

```bash
bin/spade start
```

### 2. Add the CommandLine analyzer

```bash
bin/spade control
```

At the control prompt:

```
add analyzer CommandLine
```

### 3. Add the required storage

At the control prompt, add the storage you want to query against (e.g. PostgreSQL):

```
add storage PostgreSQL
```

### 4. Set the active storage

```
set storage PostgreSQL
```

Exit the control client when done.

### 5. Start the MCP server in HTTP mode

```bash
bin/spade mcp -- \
  spade.host=localhost \
  spade.query.port=19998 \
  spade.control.port=19999 \
  mcp.server.mode=http \
  mcp.http.host.name=localhost \
  mcp.http.host.port=3000 \
  mcp.http.host.endpoint=/mcp
```

The server will listen at `http://localhost:3000/mcp`.

All of these can also be set in `cfg/spade.utility.mcp.server.Main.config` instead of passing them on the command line (command-line arguments take precedence). `spade.query.port` and `spade.control.port` are optional — the shipped default config references `commandline_query_port` and `local_control_port` in `cfg/spade.core.Kernel.config` for them.

### 6. Add the MCP server to Claude Code

```bash
claude mcp add --transport http spade http://localhost:3000/mcp
```

### 7. Run Claude Code

```bash
claude
```

SPADE tools (`list_storages`, `set_storage`, `quick_grail_query`, etc.) will be available in the session.

---

## Stdio Transport (Claude Desktop)

### 1. Update the Claude Desktop configuration

Edit `~/Library/Application Support/Claude/claude_desktop_config.json` and add the `spade` server under `mcpServers`:

```json
{
  "mcpServers": {
    "spade": {
      "command": "/path/to/spade/bin/spade",
      "args": [
        "mcp",
        "spade.host=localhost",
        "spade.query.port=19998",
        "spade.control.port=19999",
        "mcp.server.mode=stdio"
      ]
    }
  }
}
```

Replace `/path/to/spade` with the absolute path to your SPADE installation.

### 2. Start SPADE

```bash
bin/spade start
```

### 3. Add the CommandLine analyzer and storage

Follow steps 2–4 from the HTTP section above.

### 4. Start Claude Desktop

Launch the Claude Desktop app. It will spawn the MCP server process automatically using the configuration above. SPADE tools will be available in the conversation.

---

## MCP Client (CLI / Web)

`spade.utility.mcp.client.Main` is a standalone client that connects an LLM (Anthropic or a mock) to a running MCP server, useful for testing tools without a full MCP-compatible app like Claude Code/Desktop.

### 1. Start the MCP server in HTTP mode

Follow the HTTP Transport section above.

### 2. Run the client

```bash
bin/spade run-util -- spade.utility.mcp.client.Main \
  llm.type=mock \
  user.mode=cli \
  verbose=true
```

`mcp.host`, `mcp.port`, and `mcp.endpoint` were intentionally omitted above — `cfg/spade.utility.mcp.client.Main.config` sets them by default to reference `mcp.http.host.name`, `mcp.http.host.port`, and `mcp.http.host.endpoint` in `cfg/spade.utility.mcp.server.Main.config`, so they stay in sync with the server. Pass them explicitly to point at a different server.

`llm.mock.scenario` was also omitted above — it's required whenever `llm.type=mock`, and `cfg/spade.utility.mcp.client.Main.config` defaults it to `add_and_remove_neo4j_storage`, one of the premade scenarios in `cfg/spade.utility.mcp.client.llm.mock.scenario.Registry.config`. The mock LLM issues that scenario's tool calls in order against the real MCP server and verifies each one's actual result, erroring out on a mismatch — see [`llm/mock/scenario/README.md`](../../subpackage/java/src/main/java/spade/utility/mcp/client/llm/mock/scenario/README.md) in the client source for the scenario config key format.

All of these can also be set in `cfg/spade.utility.mcp.client.Main.config` instead of passing them on the command line (command-line arguments take precedence). To use the real Anthropic API instead of the mock LLM, set `llm.type=anthropic llm.anthropic.api.key=<key> llm.anthropic.model=<model>`.

`user.mode=web` is **experimental / not stable** and is not recommended for regular use; `user.mode=cli` is the supported mode.
