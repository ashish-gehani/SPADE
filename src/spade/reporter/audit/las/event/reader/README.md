# spade.reporter.audit.las.event.reader

Reader abstraction for reading audit events from an input stream.

## Reader

Abstract base class for reading audit events from an arbitrary source. Implements
`AutoCloseable`. Holds the `InputStream`, `record.Factory`, and `Factory` (event factory).

Subclasses must implement:

| Method | Description |
|--------|-------------|
| `readEvent()` | Return the next complete `Event`, or `null` at end of stream |
| `close()` | Release resources |

Constructor parameters:

| Parameter | Description |
|-----------|-------------|
| `inputStream` | The stream to read from (non-null) |
| `recordFactory` | Factory for creating records from raw lines (non-null) |
| `eventFactory` | Factory for creating events from grouped records (non-null) |

Protected accessors: `getInputStream()`, `getRecordFactory()`, `getEventFactory()`.

## InputStreamReader

Concrete `Reader` that reads audit events from an `InputStream` line by line.

### Construction

Use the static factory method for the common case:

```java
InputStreamReader.withDefaultFactories(inputStream, verbose)
```

This creates default `record.Factory` and `Factory` instances with the given `verbose` flag.
For custom factories, use the constructor directly:

```java
new InputStreamReader(inputStream, recordFactory, eventFactory)
```

### Pipeline

```
InputStream -> BufferedReader -> readLine() -> record.Factory.create()
  -> buffer by eventId -> Factory.createEvent() -> Event
```

### Event Grouping

Records are grouped by event ID without regexes. When a record with a new event ID
arrives, the buffered records for the previous event ID are flushed to `Factory`.
The final buffered event is flushed when EOF is reached. `Factory.createEvent()`
may return `null` for unrecognized record sets; those are skipped.

### Lifecycle

Stream is closed in `close()`.
