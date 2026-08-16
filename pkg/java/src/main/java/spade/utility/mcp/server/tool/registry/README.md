# Tool Registry

`Registry` builds the list of `Tool`s exposed by the MCP server: its config file lists other tools' config files, and for each one it parses that config's `tool.type` (see [the tool definition README](../definition/README.md)) to decide which concrete `Tool` to construct, then hands that config's already-parsed `Definition` to it rather than having it re-parse the file.

## Keys

| Key | Required | Value |
|---|---|---|
| `registry.tools` | yes | Comma-separated list of paths to tool config files (see [the setting conversions README](../../../../setting/convert/README.md) for CSV quoting rules). Each must be a readable file. |

## Dispatch

For each path in `registry.tools`, `Registry` parses that file's `tool.type` and constructs the matching `Tool`:

| `tool.type` | Constructed as |
|---|---|
| `SPADE_CLI` | `type/spade/cli/CLI.java` |
| `WEB_DOC` | `type/web/doc/Doc.java` |

## Errors

A missing/invalid `registry.tools` value, an unreadable tool config file, or an unhandled `tool.type`, fails with `InvalidSettingException`, naming the offending value/key and the config file path.
