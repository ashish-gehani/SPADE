# Web Doc Tool

`Doc` is a `Tool` that fetches documentation from one or more URLs and returns their combined content when queried.

Its config file carries both the tool's MCP-facing definition (`tool.name`, `tool.description`, `tool.properties.yaml`; see [the tool definition README](../../../definition/README.md)) and the URLs below, parsed by this package's own `Parser`.

## Keys

| Key | Required | Value |
|---|---|---|
| `web.doc.urls` | yes | Comma-separated list of URLs (see [the setting conversions README](../../../../../../setting/convert/README.md) for CSV quoting rules). Each is fetched and its content returned, in order. |

## Errors

A missing/blank `web.doc.urls`, or any entry in it that isn't a valid URL, fails parsing with `InvalidSettingException`, naming the offending value and the config file path.
