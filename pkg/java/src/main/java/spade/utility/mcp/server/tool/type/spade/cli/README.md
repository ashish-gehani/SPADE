# SPADE CLI Tool

`CLI` is a `Tool` that connects to a running SPADE instance over a host/port connection and exchanges data with it, via `Connection`. The shape of the exchanged data is configurable via `spade.connection.data.type`. This is a general shape, not tied to any one SPADE-side listener — SPADE's query client and control client are both examples of the kind of connection this can front.

Its config file carries both the tool's MCP-facing definition (`tool.name`, `tool.description`, `tool.properties.yaml`; see [the tool definition README](../../../definition/README.md)) and the connection settings below, parsed by this package's own `Parser`.

## Keys

| Key | Required | Value |
|---|---|---|
| `spade.host` | yes | Non-blank string. The host where the target SPADE instance is running. |
| `spade.port` | yes | Integer in `[1, 65535]`. The port the target connection is listening on. |
| `spade.keystore.serverPublicPath` | yes | Path to a readable file: the server's public keystore. |
| `spade.keystore.clientPrivatePath` | yes | Path to a readable file: the client's private keystore. |
| `spade.keystore.passwordPublic` | yes | Non-blank string. Password for the public keystore. |
| `spade.keystore.passwordPrivate` | yes | Non-blank string. Password for the private keystore. |
| `spade.connection.data.type` | yes | One of `STRING_LINE`, `QUERY_OBJECT`. |

Keystore paths and passwords are ordinary setting values, so a `$(config_file <path> <key>)` reference can be used to point at shared/existing values (e.g. the ones already defined for the SPADE kernel) instead of duplicating them in this tool's own config file. A password can also be a `$(text_file <path>)` reference to a file whose contents are the password; a keystore path cannot, since `text_file` yields the referenced file's contents rather than a path.

## Example

Reusing the keystore setup already defined for the SPADE kernel (`cfg/spade.core.Kernel.config`, at the SPADE root) instead of duplicating those values:

```
spade.keystore.serverPublicPath  = $(config_file cfg/spade.core.Kernel.config server_public_keystore)
spade.keystore.clientPrivatePath = $(config_file cfg/spade.core.Kernel.config client_private_keystore)
spade.keystore.passwordPublic    = $(config_file cfg/spade.core.Kernel.config password_public_keystore)
spade.keystore.passwordPrivate   = $(config_file cfg/spade.core.Kernel.config password_private_keystore)
```

## Errors

A missing/blank required key, or an out-of-range `spade.port`, fails parsing with `InvalidSettingException`, naming the offending key and the config file path.
