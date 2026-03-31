# spade.reporter.audit.event

Abstract base layer for typed audit events. Defines the core contracts — identity,
event structure, parsing context, production, and I/O — that concrete implementations
build on.

## ID

Abstract base for event identifiers. Wraps a `long id` and implements `Comparable<ID>`
by comparing the raw value with `Long.compare`. `toString()` returns `ID[id=<value>]`.
Subclasses provide domain-specific identity semantics.

## Event

Abstract base for audit events. Holds a single `ID` and implements `Comparable<Event>`
by delegating to `ID.compareTo`. Ordering is therefore entirely determined by the
event's identity.

## Context

Abstract marker for the parsing context passed to a `Factory`. Carries whatever
source-specific state a `Factory` implementation needs to construct an `Event`.

## Factory

Abstract factory with a single method:

```
Event create(Context context)
```

Subclasses receive a `Context` and return a concrete `Event`. Separates event
construction from the I/O layer.

## Reader

Abstract, `AutoCloseable` event source. Requires a non-null `Factory` at construction
(throws `IllegalArgumentException` otherwise) and exposes it to subclasses via
`getEventFactory()`. Subclasses implement:

| Method | Behaviour |
|--------|-----------|
| `readEvent()` | Returns the next `Event`, or `null` at end of stream |
| `close()` | Releases resources |

## Writer

Abstract, `AutoCloseable` event sink. Subclasses implement:

| Method | Behaviour |
|--------|-----------|
| `writeEvent(Event)` | Writes one event; may throw `Exception` |
| `close()` | Releases resources |

## MalformedEventException

Thrown when an event cannot be constructed. Three constructors:

| Constructor | Use |
|-------------|-----|
| `(String msg)` | Message only |
| `(String msg, ID eventId)` | Message with event identity |
| `(String msg, ID eventId, Throwable t)` | Message, identity, and cause |

A null `ID` is formatted as `(null)`.
