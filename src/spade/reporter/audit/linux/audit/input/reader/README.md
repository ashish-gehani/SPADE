# `spade.reporter.audit.linux.audit.input.reader`

This package defines the abstraction for reading raw Linux audit log lines from an underlying source, along with concrete implementations for each supported source type.

## Overview

```
reader/
├── LineReader.java     # Abstract base: one-line-at-a-time source
├── Type.java           # Enum of supported reader types
├── Factory.java        # Instantiates a LineReader for a given Type + Config
└── bridge/             # SPADE Audit Bridge implementation
    ├── LineReader.java     # Reads lines from bridge process stdout
    ├── Helper.java         # Builds a bridge Process from Config
    ├── Process.java        # Lifecycle management of the bridge subprocess
    └── ProcessConfig.java  # Configuration for the bridge subprocess
```

## Key Types

### `LineReader` (abstract)

The central abstraction. Reads one raw audit log line per call and returns `null` at end-of-stream. Implements `AutoCloseable` so it can be used in try-with-resources.

```java
String line;
while ((line = reader.readLine()) != null) {
    // process line
}
```

### `Type`

Enum identifying which concrete `LineReader` to create. Currently defined:

| Value | Description |
|---|---|
| `SPADE_AUDIT_BRIDGE` | Reads from the stdout of a `spade-audit-bridge` subprocess |

### `Factory`

Creates a `LineReader` for a given `Type` and `Config`. Throws `IllegalArgumentException` for null arguments or unknown types.

```java
LineReader reader = new Factory().create(Type.SPADE_AUDIT_BRIDGE, config);
```

## `bridge/` Subpackage

Implements `LineReader` for the `SPADE_AUDIT_BRIDGE` type.

- **`Process`** — launches and manages the `spade-audit-bridge` subprocess, captures stderr for logging, and exposes the process stdout stream.
- **`ProcessConfig`** — holds all parameters needed to build the bridge command line (bridge path, mode, input log list, input directory, socket path, wait-for-log flag, units flag, merge-unit).
- **`Helper`** — convenience factory that constructs a `Process` from a `Config` object.
- **`LineReader`** — wraps a `bridge.Process` stdout in a `BufferedReader` and delegates `readLine()` to it. Handles startup and teardown.

## Adding a New Reader Type

1. Add a constant to `Type`.
2. Create a subpackage (e.g., `file/`) with a concrete `LineReader` subclass.
3. Add a `case` in `Factory.create()` that instantiates it.
