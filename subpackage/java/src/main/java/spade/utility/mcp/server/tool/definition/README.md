# Tool Definition

Parses a tool's definition — its name, description, type, and the properties it accepts — from a config file, for use when registering a tool exposed to MCP clients.

## Entry point

`Parser.parse(configFilePath)` reads the config file at the given path and returns the parsed definition. Config files follow the `spade.utility.setting` format; see [its README](../../../../setting/README.md) for the general key/value/reference rules.

## Config file structure

| Key | Required | Value |
|---|---|---|
| `tool.name` | yes | Non-blank string. |
| `tool.description` | yes | Non-blank string. |
| `tool.type` | yes | One of `Type`'s constants (currently `SPADE_CLI`, `WEB_DOC`); tells the registry which concrete tool to build from this config file. |
| `tool.properties.yaml` | no | A `$(text_file <path>)` reference to a YAML file listing the tool's properties. If omitted, the tool has no properties. |

## Properties YAML structure

The file referenced by `properties.yaml` is a YAML list, one entry per property:

| Field | Required | Value |
|---|---|---|
| `name` | yes | Non-blank string. |
| `type` | yes | Non-blank string. |
| `description` | yes | Non-blank string. |
| `required` | yes | Boolean. |
| `possibleValues` | no | List of strings. Defaults to empty if omitted. |

Example:

```yaml
- name: format
  type: string
  description: Output format.
  required: true
  possibleValues: [json, csv]
- name: limit
  type: integer
  description: Maximum number of results.
  required: false
```

## Errors

Any missing/blank required field — at the config level or within a property entry — fails parsing with `InvalidSettingException`, naming the offending key/field and the config file path.
