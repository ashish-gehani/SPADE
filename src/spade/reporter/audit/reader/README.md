# spade.reporter.audit.reader

High-level reader abstraction for reading Linux audit events from various sources.

## Overview

```
Reader  (abstract base)
  └── spade.audit.bridge.Reader
```

`Reader` is the abstract base class. Each subpackage provides a concrete
implementation along with a static `Create.reader(...)` factory method for
constructing it.

## Reader

Abstract base class implementing `AutoCloseable`. Holds shared factory
instances passed in by the caller and exposes them to subclasses via
protected getters.

| Constructor parameter | Type | Description |
|-----------------------|------|-------------|
| `recordFactory` | `las.event.record.Factory` | Parses raw audit lines into `Record` objects |
| `eventFactory` | `las.event.Factory` | Groups `Record` sets into typed `Event` objects |

| Protected getter | Returns |
|------------------|---------|
| `getRecordFactory()` | `las.event.record.Factory` |
| `getEventFactory()` | `las.event.Factory` |

Subclasses must implement:

| Method | Description |
|--------|-------------|
| `readEvent()` | Return the next `Event`, or `null` at end of source |
| `close()` | Release resources |

## spade.audit.bridge

Reads events from the stdout of a running SPADE audit bridge process.

### Classes

| Class | Description |
|-------|-------------|
| `ProcessConfig` | Configuration for the bridge process (path, mode, socket/log/dir args, units, mergeUnit) |
| `Process` | Manages the bridge OS process lifecycle: start, stop, kill, close |
| `Reader` | Extends `spade.reporter.audit.reader.Reader`. Starts the process and wraps its stdout in a `las.event.reader.InputStreamReader` |
| `Create` | Static factory. `Create.reader(input, auditConfiguration, recordFactory, eventFactory)` builds `ProcessConfig` → `Process` → `Reader` |

### Create.reader

```java
public static Reader reader(
    Input input,
    AuditConfiguration auditConfiguration,
    las.event.record.Factory recordFactory,
    las.event.Factory eventFactory
) throws Exception
```

Derives bridge arguments from `Input` (path, mode, log/dir/socket paths,
waitForLog) and `AuditConfiguration` (units, mergeUnit), constructs the
`Process`, and returns a started `Reader`.
