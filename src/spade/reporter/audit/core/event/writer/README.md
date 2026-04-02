# spade.reporter.audit.core.event.writer

Abstract base layer for typed audit-event sinks. Defines the generic
`Writer` contract and the event-level `Metrics` that every writer
implementation tracks.

## Writer<T extends Event>

Abstract, `AutoCloseable` event sink parameterised on the concrete
`Event` subtype `T` it accepts. Subclasses implement:

| Method | Behaviour |
|--------|-----------|
| `long writeEvent(T)` | Writes one event; returns the number of bytes written; may throw `Exception` |
| `close()` | Releases resources |

## Metrics

Abstract counters for an event-writing pipeline. Tracks only what the
abstract `Writer` knows about — events:

| Counter | Incremented by |
|---------|----------------|
| `eventsWritten` | Each event fully written |
| `writeFailures` | Each `writeEvent()` call that threw |

Mutators are `protected`; getters, `toString()`, and `log()` are public.
`toString()` returns the full snapshot line (class name, wall-clock
timestamp, and all counters); `log()` writes that line at `INFO` level.
Subclasses override `toString()` to append sink-specific counters.
