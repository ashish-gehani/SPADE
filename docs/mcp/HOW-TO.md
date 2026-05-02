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
bin/spade mcp \
  --spadeHost=localhost \
  --spadeQueryPort=19998 \
  --spadeControlPort=19999 \
  --mcpServerMode=http \
  --mcpHttpHostName=localhost \
  --mcpHttpHostPort=3000 \
  --mcpHttpHostEndpoint=/mcp
```

The server will listen at `http://localhost:3000/mcp`.

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
        "--spadeHost=localhost",
        "--spadeQueryPort=19998",
        "--spadeControlPort=19999",
        "--mcpServerMode=stdio"
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
