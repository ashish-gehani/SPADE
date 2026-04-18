# spade.reporter.audit.core.event

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

## Factory<T extends Event, V extends Context>

Abstract factory parameterised on the concrete `Event` subtype `T` it produces
and the `Context` subtype `V` it consumes. Single method:

```
T create(V context)
```

Subclasses receive a typed `V` and return a typed `T`. Separates event
construction from the I/O layer.

## Reader / Writer

Live in dedicated subpackages alongside their abstract `Metrics`:

- [`reader/`](reader/README.md) — `Reader<T extends Event, V extends Context>` and reader `Metrics`
- [`writer/`](writer/README.md) — `Writer<T extends Event>` and writer `Metrics`

## MalformedEventException

Thrown when an event cannot be constructed. Three constructors:

| Constructor | Use |
|-------------|-----|
| `(String msg)` | Message only |
| `(String msg, ID eventId)` | Message with event identity |
| `(String msg, ID eventId, Throwable t)` | Message, identity, and cause |

A null `ID` is formatted as `(null)`.
