# `spade.reporter.audit.linux.audit.input`

This package assembles the full audit-reading pipeline: from raw lines coming off a source (e.g. the SPADE Audit Bridge subprocess) up to typed, buffered `Event` objects ready for consumption.

## Pipeline

```
reader.LineReader          raw text lines from a source
       │
       ▼
RecordReader               parses each line into a typed Record
       │
       ▼
EventReader                groups Records by (ID, Timestamp) into Events
       │
       ▼
BufferedEventReader        decouples producer from consumer via a Channel
```

`Helper.createReader(Config)` wires all four stages together and returns a ready-to-start `BufferedEventReader`.

## Classes

### `Config`

Immutable value object that carries all parameters needed to build the pipeline:

| Field | Purpose |
|---|---|
| `Input` | Bridge path, mode, socket path, log file, etc. |
| `AuditConfiguration` | Units / merge-unit settings |
| `channel.Config` | Buffer capacity, read timeout |
| `lineReaderType` | Which `reader.LineReader` implementation to use |
| `recordFactoryVerbose` | Enable verbose logging in the record parser |
| `eventFactoryVerbose` | Enable verbose logging in the event factory |

### `RecordReader`

Wraps a `reader.LineReader`. On each call to `readRecord()` it fetches the next line, parses it through the `record.Factory`, and returns the `Record`. Malformed lines are logged and skipped; unrecognised lines (factory returns `null`) are silently dropped. Returns `null` at end-of-stream.

### `EventReader`

Wraps a `RecordReader`. Accumulates `Record` objects that share the same `(ID, Timestamp)` into a `Context`. When the ID/Timestamp changes the buffered context is flushed through the `event.Factory` to produce a typed `Event`. Returns `null` at end-of-stream.

### `BufferedEventReader`

Wraps an `EventReader` with an asynchronous `Channel`. A daemon pump thread drains the `EventReader` and writes events into the channel. Callers read from the channel via `readEvent()`, decoupling audit log parsing from downstream processing. The channel is closed when the pump thread reaches end-of-stream or hits an unrecoverable error.

Call `start()` before the first `readEvent()`.

### `Helper`

Convenience factory. `createReader(Config)` constructs the full pipeline and returns a `BufferedEventReader` (not yet started).

```java
BufferedEventReader reader = new Helper().createReader(config);
reader.start();

Event event;
while ((event = reader.readEvent()) != null) {
    // process event
}
reader.close();
```

## Subpackages

- [`reader/`](reader/README.md) — `LineReader` abstraction and concrete implementations (e.g. SPADE Audit Bridge).
