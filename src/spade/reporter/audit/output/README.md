# spade.reporter.audit.output

Writer abstraction for writing Linux audit events to a destination.

## Overview

```
Config  (abstract base)
  ├── file.Config
  ├── rotating.file.Config
  └── noop.Config

Writer  (abstract base)
  ├── file.Writer
  ├── rotating.file.Writer
  └── noop.Writer

Factory  →  creates Writer from Config
Type     →  enum of known writer types
```

## Type

Enum of available writer types.

| Constant | Description |
|----------|-------------|
| `FILE` | Write to a single file |
| `ROTATING_FILE` | Write to a file with rotation by byte threshold |
| `NO_OP` | Discard all events |

## Config

Abstract base class for writer configuration. Holds the `Type`.

| Constructor parameter | Type | Description |
|-----------------------|------|-------------|
| `type` | `Type` | Identifies which writer implementation to use (non-null) |

| Getter | Returns |
|--------|---------|
| `getType()` | `Type` |

## Writer

Abstract base class implementing `AutoCloseable`. Holds the `Config` passed at
construction and exposes it via a `final` getter.

| Constructor parameter | Type | Description |
|-----------------------|------|-------------|
| `config` | `Config` | Writer configuration (non-null) |

| Getter | Returns |
|--------|---------|
| `getConfig()` | `Config` |

Subclasses must implement:

| Method | Signature | Description |
|--------|-----------|-------------|
| `writeEvent` | `long writeEvent(Event event) throws Exception` | Write a complete audit event; returns bytes written |
| `close` | `void close()` | Flush and release resources |

## Factory

Instance method; dispatches on `config.getType()` to construct the appropriate `Writer`.

```java
Writer writer = new Factory().create(config);
```

Throws `IllegalArgumentException` if `config` is null or the type is unknown.

---

## file

Writes audit events to a single file using `las.event.output.stream.Writer`.

### file.Config

Extends `output.Config` with `type = FILE`.

| Constructor parameter | Type | Description |
|-----------------------|------|-------------|
| `filePath` | `String` | Output file path (non-null) |

| Getter | Returns |
|--------|---------|
| `getFilePath()` | `String` |

### file.Writer

```java
public Writer(Config config) throws Exception
```

Opens a `FileOutputStream` at `config.getFilePath()` and wraps it in an
`las.event.output.stream.Writer`. `writeEvent` delegates to the `las.event.output.stream.Writer`.
`close()` closes the `las.event.output.stream.Writer`, logging any error at `SEVERE`.

---

## rotating.file

Writes audit events to a sequence of files, rotating when the byte threshold is
reached after writing each event.

File naming: `basePath`, `basePath.1`, `basePath.2`, ...

### rotating.file.Config

Extends `output.Config` with `type = ROTATING_FILE`.

| Constructor parameter | Type | Description |
|-----------------------|------|-------------|
| `basePath` | `String` | Base output file path (non-null) |
| `rotateAfterBytes` | `long` | Byte threshold for rotation; values `<= 0` disable rotation |

| Getter | Returns |
|--------|---------|
| `getBasePath()` | `String` |
| `getRotateAfterBytes()` | `long` |

### rotating.file.Writer

```java
public Writer(Config config) throws Exception
```

Opens the first file at `basePath` via `file.Writer`. On each `writeEvent` call:

1. Delegates to the current `file.Writer`; accumulates `totalBytesWritten`.
2. If rotation is enabled (`rotateAfterBytes > 0`) and `totalBytesWritten >= rotateAfterBytes`, closes the current file, increments the file index, resets `totalBytesWritten` to 0, and opens the next file at `basePath.<index>`.
3. Returns the bytes written by the current call (0 if `event` is null).

`close()` closes the current `file.Writer` (logging `SEVERE` on error) and sets it to null in a `finally` block.

---

## noop

Discards all events silently.

### noop.Config

Extends `output.Config` with `type = NO_OP`. No additional fields or parameters.

### noop.Writer

```java
public Writer(Config config)
```

`writeEvent` always returns `0`. `close` is a no-op.
