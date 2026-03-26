# spade.reporter.audit.writer

Writer abstraction for writing audit events to a destination.

## Writer

Abstract base class for all writers. Implements `AutoCloseable`.

Subclasses must implement:

| Method | Description |
|--------|-------------|
| `writeEvent(Event)` | Write a complete audit event; returns bytes written |
| `close()` | Flush and release resources |

## FileWriter

Writes audit events to a file. Uses `OutputStreamWriter` internally.

Constructor parameters:

| Parameter | Description |
|-----------|-------------|
| `filePath` | Path of the output file (non-null) |

## RotatingFileWriter

Writes audit events to a file with rotation when a byte threshold is reached.

File naming: `basePath`, `basePath.1`, `basePath.2`, ...

Constructor parameters:

| Parameter | Description |
|-----------|-------------|
| `basePath` | Base output file path (non-null) |
| `rotateAfterBytes` | Byte threshold for rotation; values < 1 disable rotation |

## Config

Abstract base class for writer configuration. Holds the `Type`.

## FileWriterConfig

Config for `FileWriter`. Type: `FILE`.

| Parameter | Description |
|-----------|-------------|
| `filePath` | Path of the output file (non-null) |

## RotatingFileWriterConfig

Config for `RotatingFileWriter`. Type: `ROTATING_FILE`.

| Parameter | Description |
|-----------|-------------|
| `basePath` | Base output file path (non-null) |
| `rotateAfterBytes` | Byte threshold for rotation |

## Type

Enum of available writer types: `FILE`, `ROTATING_FILE`.

## Factory

Creates a `Writer` from a `Config`.

```java
Writer writer = new Factory().create(config);
```

Throws `IllegalArgumentException` if config is null or the type is unknown.
