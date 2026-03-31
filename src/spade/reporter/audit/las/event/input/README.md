# spade.reporter.audit.las.event.input

Reader abstraction for reading audit events from an input stream.

## Overview

```
Reader  (abstract base)
  └── stream.Reader
```

## Reader

Abstract base class for reading audit events from an arbitrary source. Implements
`AutoCloseable`. Holds the `InputStream`, `record.Factory`, and `Factory` (event factory).

Constructor parameters (all non-null):

| Parameter | Type | Description |
|-----------|------|-------------|
| `inputStream` | `InputStream` | The stream to read from |
| `recordFactory` | `las.event.record.Factory` | Creates `Record` objects from raw lines |
| `eventFactory` | `las.event.Factory` | Creates `Event` objects from grouped records |

Protected accessors: `getInputStream()`, `getRecordFactory()`, `getEventFactory()`.

Subclasses must implement:

| Method | Description |
|--------|-------------|
| `readEvent()` | Return the next complete `Event`, or `null` at end of stream |
| `close()` | Release resources |

## stream.Reader

Concrete `Reader` that reads audit events from an `InputStream` line by line.

### Constructor

```java
public Reader(
    InputStream inputStream,
    las.event.record.Factory recordFactory,
    las.event.Factory eventFactory
) throws Exception
```

Wraps `inputStream` in a `BufferedReader`.

### Pipeline

```
InputStream -> BufferedReader -> readLine() -> record.Factory.create()
  -> buffer by eventId -> Factory.createEvent() -> Event
```

### Event grouping

Records are grouped by event ID using `Record.getEventId()` — no regex. When a
record with a new event ID arrives, the buffered records for the previous event ID
are flushed to `Factory.createEvent()`. The final buffered group is flushed when
EOF is reached. Lines that `record.Factory.create()` returns `null` for are
skipped. `Factory.createEvent()` may also return `null` for unrecognised record
sets; those are skipped too.

### Lifecycle

`close()` closes the `BufferedReader`, logging any error at `SEVERE`.
