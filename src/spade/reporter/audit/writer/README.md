# spade.reporter.audit.writer

Writer abstraction for writing audit events to a destination.

## Writer

Abstract base class for all writers. Implements `AutoCloseable`. Requires a non-null `Config` at construction; the config is accessible via `getConfig()`.

Subclasses must implement:

| Method | Description |
|--------|-------------|
| `writeEvent(Event)` | Write a complete audit event; returns bytes written |
| `close()` | Flush and release resources |

## Config

Abstract base class for writer configuration. Holds the `Type`. Passed to `Writer(Config)` and stored for retrieval via `getConfig()`.

## Type

Enum of available writer types: `FILE`, `ROTATING_FILE`, `NO_OP`.

## Factory

Creates a `Writer` from a `Config`.

```java
Writer writer = new Factory().create(config);
```

Throws `IllegalArgumentException` if config is null or the type is unknown.

---

## file.File (`writer/file/`)

Writes audit events to a file. Uses `OutputStreamWriter` internally.

Constructor: `File(file.Config config)`

## file.Config

Config for `file.File`. Type: `FILE`.

| Parameter | Description |
|-----------|-------------|
| `filePath` | Path of the output file (non-null) |

---

## rotating.file.File (`writer/rotating/file/`)

Writes audit events to a file with rotation when a byte threshold is reached.

File naming: `basePath`, `basePath.1`, `basePath.2`, ...

Constructor: `File(rotating.file.Config config)`

## rotating.file.Config

Config for `rotating.file.File`. Type: `ROTATING_FILE`.

| Parameter | Description |
|-----------|-------------|
| `basePath` | Base output file path (non-null) |
| `rotateAfterBytes` | Byte threshold for rotation; values < 1 disable rotation |

---

## noop.NoOp (`writer/noop/`)

Discards all events. `writeEvent` always returns 0; `close` is a no-op.

Constructor: `NoOp(noop.Config config)`

## noop.Config

Config for `noop.NoOp`. Type: `NO_OP`. No parameters.
