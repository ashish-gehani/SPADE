# `spade.reporter.audit.linux.audit.tee`

This package joins the [`input`](../input/README.md) and [`output`](../output/README.md) pipelines into a single caller-facing reader. Each event read from the input pipeline is mirrored to the output pipeline and then returned to the caller (the Audit reporter).

## Pipeline

```
input.BufferedEventReader      reads typed Events from the source
            │
            ▼
           Tee  ──────────────► output.EventWriter (best-effort mirror)
            │
            ▼
          caller                consumes the same Event
```

`Helper.createTee(Config)` builds both sides via `input.Helper` / `output.Helper` and returns a ready-to-start `Tee`.

## Classes

### `Config`

Immutable value object that bundles everything needed to build a `Tee`:

| Field | Purpose |
|---|---|
| `input.Config` | Configuration for the reading pipeline |
| `output.Config` | Configuration for the writing pipeline |
| `verbose` | Enable per-event `FINE` logging in the `Tee` |

### `Tee`

Extends `core.event.Reader`. Wraps a `BufferedEventReader` and an `EventWriter`. On each `readEvent()` call:

1. Read the next event from the `BufferedEventReader` (returns `null` at end-of-stream).
2. Mirror it to the `EventWriter`. A write failure is logged at `WARNING` and does **not** prevent the event from being returned.
3. Return the event to the caller.

`Tee` carries no metrics state of its own; `getReaderMetrics()` / `getWriterMetrics()` delegate to the underlying reader and writer.

Call `start()` before the first `readEvent()`.

### `Helper`

Convenience factory. `createTee(Config)` constructs the full pipeline and returns a `Tee` (not yet started).

```java
Tee tee = new Helper().createTee(config);
tee.start();

Event event;
while ((event = tee.readEvent()) != null) {
    // process event
}
tee.close();
```
