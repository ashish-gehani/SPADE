# spade.reporter.audit.reader

High-level reader abstraction for reading Linux audit events from various sources.

## Overview

```
Config  ──►  State  ──►  Factory  ──►  Reader
```

A `Config` carries both the source parameters and the `verboseEventFactory` flag.
A `State` is constructed from a `Config` and holds the shared `record.Factory` and
`event.Factory` instances. `Factory.create(config, state)` selects the right `Reader`
implementation and passes the factories from `State` into it.

## Type

Enum identifying the reader implementation.

| Value | Description |
|-------|-------------|
| `Process` | Read from the stdout of a `java.lang.Process` |
| `File` | Read from a file on disk |
| `LASRotatedFilesByBasePath` | Read from LAS rotated files identified by base path |
| `LASRotatedFilesInDirectory` | Read from LAS rotated files in a directory |

## Config

Abstract base class. Holds the `Type` and `verboseEventFactory` flag used to
initialise factories in `State`.

| Subclass | Extra fields |
|----------|-------------|
| `ProcessReaderConfig` | `process` (`java.lang.Process`) |
| `FileReaderConfig` | `filePath` (`String`) |

## State

Constructed from a `Config`. Initialises and owns:

- `las.event.record.Factory` — parses raw audit lines into `Record` objects
- `las.event.Factory` — groups `Record` sets into typed `Event` objects

Both factories are created with the `verboseEventFactory` flag from the config.

## Reader

Abstract base class implementing `AutoCloseable`. Subclasses must implement:

| Method | Description |
|--------|-------------|
| `readEvent()` | Return the next `Event`, or `null` at end of source |
| `close()` | Release resources |

### ProcessReader

Wraps `process.getInputStream()` in a `las.event.reader.InputStreamReader`.

### FileReader

Opens a `FileInputStream` on the given path and wraps it in a
`las.event.reader.InputStreamReader`.

## Factory

`Factory.create(config, state)` dispatches on `config.getType()` and constructs
the matching `Reader`, passing the factories from `state` into it.

## Usage

```java
final FileReaderConfig config = new FileReaderConfig("/var/log/audit/audit.log", false);
final State state = new State(config);
final Reader reader = new Factory().create(config, state);

Event event;
while((event = reader.readEvent()) != null){
    // handle event
}
reader.close();
```
