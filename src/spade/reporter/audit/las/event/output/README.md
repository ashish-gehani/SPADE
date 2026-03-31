# spade.reporter.audit.las.event.output

Writer abstraction for writing audit events to an arbitrary destination.

## Overview

```
Writer  (abstract base)
  └── stream.Writer
```

## Writer

Abstract base class for writing audit events. Implements `AutoCloseable`. Holds the
`OutputStream` passed at construction.

| Constructor parameter | Type | Description |
|-----------------------|------|-------------|
| `outputStream` | `OutputStream` | The stream to write to (non-null) |

| Accessor | Returns |
|----------|---------|
| `getStream()` | `OutputStream` |

Subclasses must implement:

| Method | Description |
|--------|-------------|
| `writeEvent(Event)` | Write a complete audit event; returns bytes written |
| `close()` | Flush and release resources |

## stream.Writer

Concrete `Writer` that writes audit record lines to an `OutputStream` via a
`PrintWriter` (auto-flush enabled).

### Constructor

```java
public Writer(OutputStream outputStream)
```

Wraps `outputStream` in a `PrintWriter` with auto-flush.

### writeEvent

Iterates over all `Record` objects in the event. For each record, writes
`r.getRawRecord()` via `PrintWriter.println()` and accumulates
`rStr.length() + 1` bytes (the `+1` accounts for the newline). Returns total
bytes written. Returns `0` if `event` is null. Throws `MalformedEventException`
if any record or its raw string is `null`.

### close

Flushes then closes the `PrintWriter`, logging any error at `SEVERE`.
