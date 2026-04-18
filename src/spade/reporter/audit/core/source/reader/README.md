# spade.reporter.audit.core.event.reader

Abstract base layer for typed audit-event sources. Defines the generic
`Reader` contract and the event-level `Metrics` that every reader
implementation tracks.

## Reader<T extends Event, V extends Context>

Abstract, `AutoCloseable` event source parameterised on the concrete
`Event` subtype `T` it produces and the `Context` subtype `V` its
`Factory` consumes. Requires a non-null `Factory<T, V>` at construction
(throws `IllegalArgumentException` otherwise) and exposes it to
subclasses via `getEventFactory()`. Subclasses implement:

| Method | Behaviour |
|--------|-----------|
| `T readEvent()` | Returns the next event, or `null` at end of stream |
| `close()` | Releases resources |

## Metrics

Abstract counters for an event-reading pipeline. Tracks only what the
abstract `Reader` knows about — events:

| Counter | Incremented by |
|---------|----------------|
| `eventsRead` | Each event delivered to the caller |

Mutators are `protected`; getters, `toString()`, and `log()` are public.
`toString()` returns the full snapshot line (class name, wall-clock
timestamp, and all counters); `log()` writes that line at `INFO` level.
Subclasses override `toString()` to append source-specific counters.
