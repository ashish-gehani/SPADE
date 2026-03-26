# spade.reporter.audit.las.event.writer

Writer abstraction for writing audit events to an arbitrary destination.

## Writer

Abstract base class for writing audit events. Implements `AutoCloseable`. Holds the
`OutputStream` passed at construction.

Constructor parameters:

| Parameter | Description |
|-----------|-------------|
| `outputStream` | The stream to write to (non-null) |

Subclasses must implement:

| Method | Description |
|--------|-------------|
| `writeEvent(Event)` | Write a complete audit event; returns bytes written |
| `close()` | Flush and release resources |

Protected accessor: `getStream()`.

## OutputStreamWriter

Concrete `Writer` that writes audit record lines to an `OutputStream`.

### Writing

`writeEvent(Event)` iterates over all `Record` objects in the event and writes each
raw record string via `PrintWriter.println()`. Returns the number of bytes written for
that call. Throws `MalformedEventException` if any record or its raw string is `null`.

