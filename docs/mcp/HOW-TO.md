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
  spade_host=localhost \
  spade_query_port=19998 \
  spade_control_port=19999 \
  mcp_server_mode=http \
  mcp_http_host_name=localhost \
  mcp_http_host_port=3000 \
  mcp_http_host_endpoint=/mcp
```

The server will listen at `http://localhost:3000/mcp`.

All of these can also be set in `cfg/spade.utility.mcp.server.Server.config` instead of passing them on the command line (command-line arguments take precedence). `spade_query_port` and `spade_control_port` are optional — if omitted they fall back to `commandline_query_port` and `local_control_port` in `cfg/spade.core.Kernel.config`.

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
        "spade_host=localhost",
        "spade_query_port=19998",
        "spade_control_port=19999",
        "mcp_server_mode=stdio"
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
  llm_type=mock \
  user_client_mode=cli \
  verbose=true
```

`mcp_url` was intentionally omitted above — when absent it's constructed from `mcp_http_host_name`, `mcp_http_host_port`, and `mcp_http_host_endpoint` in `cfg/spade.utility.mcp.server.Server.config`. Pass `mcp_url=http://<host>:<port><endpoint>` explicitly to point at a different server.

All of these can also be set in `cfg/spade.utility.mcp.client.user.Client.config` instead of passing them on the command line (command-line arguments take precedence). To use the real Anthropic API instead of the mock LLM, set `llm_type=anthropic anthropic_api_key=<key> anthropic_model=<model>`.

`user_client_mode=web` is **experimental / not stable** and is not recommended for regular use; `user_client_mode=cli` is the supported mode.
