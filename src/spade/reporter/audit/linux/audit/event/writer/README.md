# `spade.reporter.audit.linux.audit.output`

This package assembles the full audit-writing pipeline: from typed `Event` objects down to raw lines written to a destination (e.g. a file or a rotating set of files).

## Pipeline

```
EventWriter                splits an Event into its constituent Records
       │
       ▼
RecordWriter               serialises each Record to its raw line via getRawRecord()
       │
       ▼
writer.LineWriter          writes raw text lines to a destination
```

`Helper.createWriter(Config)` wires all three stages together and returns a ready-to-use `EventWriter`.

## Classes

### `Config`

Wraps an `OutputLog` and exposes the parameters needed to build the pipeline. The `LineWriter` type is derived automatically:

| `OutputLog` state | Derived `Type` |
|---|---|
| `isEnabled() == false` | `NO_OP` |
| `isEnabled() && !isRotationEnabled()` | `FILE` |
| `isEnabled() && isRotationEnabled()` | `ROTATING_FILE` |

The file path comes from `OutputLog.getOutputLogPath()`. The rotation threshold (in bytes) is derived from `OutputLog.getRotateLogAfterLines() * OutputLog.APPROXIMATE_BYTES_PER_LINE`, since `OutputLog` expresses rotation in lines (audit records) but the rotating `LineWriter` operates on bytes. `snapshotIntervalMs` configures the interval at which writer `Metrics` snapshots are logged.

### `RecordWriter`

Wraps a `writer.LineWriter`. On each call to `writeRecord(Record)` it serialises the record via `Record.getRawRecord()` and delegates to the underlying `LineWriter`. Returns the number of bytes written.

### `EventWriter`

Extends `core.event.writer.Writer<Event>`. Wraps a `RecordWriter`. On each call to `writeEvent(Event)` it iterates the event's records in order and writes each one through the `RecordWriter`. Returns the total bytes written across all records.

Holds a `Metrics` instance updated on every `writeEvent()` call (success or failure); retrieve it via `getMetrics()`. The `Config` is passed at construction (retrievable via `getConfig()`); if `Config.getSnapshotIntervalMs() > 0`, a `ScheduledExecutorService` is started at construction that logs a metrics snapshot (`Metrics.log()`) at that interval and is shut down in `close()`.

### `Metrics`

Extends `core.event.writer.Metrics` (which provides `eventsWritten` and `writeFailures`). Adds the Linux-audit-specific counters:

| Counter | Meaning |
|---|---|
| `eventsWritten` | Events fully written (inherited) |
| `recordsWritten` | Records written to the underlying `LineWriter` |
| `bytesWritten` | Total bytes written |
| `writeFailures` | `writeEvent()` calls that threw (inherited) |

Mutators are package-private/protected; getters, `toString()` (full snapshot line) and `log()` are public.

### `Helper`

Convenience factory. `createWriter(Config)` constructs the full pipeline and returns a ready-to-use `EventWriter`.

```java
EventWriter writer = new Helper().createWriter(config);

for (final Event event : events) {
    writer.writeEvent(event);
}
writer.close();
```

## Subpackages

- [`writer/`](writer/) — `LineWriter` abstraction and concrete implementations (`FILE`, `ROTATING_FILE`, `NO_OP`).
