# Scenarios

A `Scenario` is a named, ordered list of `ToolCall`s (see [../ToolCall.java](../ToolCall.java)) that the mock LLM ([../Mock.java](../Mock.java)) replays: it issues each `ToolCall`'s tool name/input as a real `tool_use` against the connected MCP server (not fabricated), then checks the server's actual response against that step's expected result before moving to the next one, erroring out on a mismatch. `Registry` loads the available `Scenario`s from a config file, built on `spade.utility.setting` (see [setting/README.md](../../../../../../utility/setting/README.md)).

## Why indexed keys

`spade.utility.setting` has no way to enumerate keys by prefix — only direct lookup of a known key (see [setting/Setting.java](../../../../../../utility/setting/Setting.java)). Since a config file can't declare an arbitrary-length list any other way, scenarios and their tool calls are numbered starting at `1`, and `Registry` keeps incrementing the index and looking the next one up until a `name` key is absent, which ends that list.

## Keys

For scenario `<i>` (starting at `1`) and its tool call `<j>` (starting at `1`, within that scenario):

| Key | Required | Meaning |
| --- | --- | --- |
| `mock.scenario.<i>.name` | yes | Unique name of the scenario; absence ends the scenario list. |
| `mock.scenario.<i>.tool.<j>.name` | yes | Unique name of this tool-call step; absence ends this scenario's tool-call list. |
| `mock.scenario.<i>.tool.<j>.tool.name` | yes | The actual MCP tool name to call (e.g. `list_storages`). |
| `mock.scenario.<i>.tool.<j>.input` | no | A JSON object literal of arguments to call the tool with; defaults to `{}` if absent. |
| `mock.scenario.<i>.tool.<j>.result` | yes | Substring expected to appear in the tool's actual result, used to verify the server behaved as expected. |

A missing/empty value for any required key, or a malformed/non-object value for `input`, is rejected with `InvalidSettingException`.

## `input` format

`input`'s value is a raw JSON object, e.g.:

```
mock.scenario.1.tool.2.input = {"storageName": "Neo4j"}
```

No quoting or escaping is needed even though the value contains spaces and double quotes: a config file line is a single key/value pair, so (unlike CLI arguments) the entire remainder of the line after `=` is taken as the value verbatim — the only special case is a value that itself starts with `"`, which triggers quote-stripping/unescaping (see `Literal.parse` in [setting/value/Literal.java](../../../../../../utility/setting/value/Literal.java)). Since `{...}` doesn't start with `"`, it passes through untouched and is then parsed as JSON by `Registry`.

## `result` and why it's a substring check

`result` is compared with `String.contains`, not equality. The real MCP server's `CallToolResult.content()` is serialized to a JSON string (e.g. `[{"type":"text","text":"No storages added."}]`) before `Mock` sees it, so an exact match would require encoding that wrapper here too. A substring check lets `result` just hold the meaningful part of the expected text.

## Example

See `cfg/spade.utility.mcp.client.llm.mock.scenario.Registry.config` (at the SPADE root) for a complete scenario.
