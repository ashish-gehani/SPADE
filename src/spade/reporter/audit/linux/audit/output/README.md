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

The file path comes from `OutputLog.getOutputLogPath()` and the rotation threshold from `OutputLog.getRotateLogAfterLines()`.

### `RecordWriter`

Wraps a `writer.LineWriter`. On each call to `writeRecord(Record)` it serialises the record via `Record.getRawRecord()` and delegates to the underlying `LineWriter`. Returns the number of bytes written.

### `EventWriter`

Wraps a `RecordWriter`. On each call to `writeEvent(Event)` it iterates the event's records in order and writes each one through the `RecordWriter`. Returns the total bytes written across all records.

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
