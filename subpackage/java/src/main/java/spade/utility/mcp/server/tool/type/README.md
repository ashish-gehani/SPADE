# Tool Types

`Type` enumerates the kinds of `Tool` the [registry](../registry/README.md) knows how to build from a config file's `tool.type` (see [the tool definition README](../definition/README.md)). Each constant has a matching subpackage here implementing that kind.

## Layout

```
type/
├── Type.java          SPADE_CLI | WEB_DOC
├── spade/
│   └── cli/              SPADE_CLI: a general SPADE host/port connection tool; see its README
└── web/
    └── doc/               WEB_DOC: fetches documentation from configured URLs; see its README
```
