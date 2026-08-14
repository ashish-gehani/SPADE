# Tools

The MCP tools exposed to clients by the SPADE MCP server. See [the server README](../README.md) for how this fits into the rest of the server.

## Layout

```
tool/
├── Tool.java                tool interface
├── definition/              parses a tool's config-driven MCP definition; see [definition/README.md](definition/README.md)
├── registry/                builds the available tools from config; see [registry/README.md](registry/README.md)
└── type/                    the concrete kinds of tool a config can declare; see [type/README.md](type/README.md)
```
