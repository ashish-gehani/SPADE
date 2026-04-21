# System Provenance — Standalone Component

A self-contained component responsible for generating system provenance in a structured, well-defined manner.

## Background

System provenance is composed of a finite set of **provenance structures** — each a well-defined ordering and grouping of provenance elements (vertices and edges).

**Example — `Access`:** A primitive structure representing a single edge where a system resource vertex is accessed by a system process vertex. The ordering is: generate process vertex → generate resource vertex → emit edge.

Provenance structures may also be **composite**. For example, `Rename` — where a process assigns a new name to an existing resource — is composed of three primitives: `Access` (read existing resource), `Create` (produce resource with new identity), and `Update` (link new resource to existing resource).

---

## API

### `type/Provenanceable`

Interface implemented by all types that describe themselves as provenance annotations. Requires two methods:

- `getKeyAnnotations()` — key-value annotations that **uniquely identify** the object at the system level.
- `getExtraAnnotations()` — supplementary key-value annotations with no uniqueness requirement.

### `type/AbstractProcess`

Abstract class representing a system process. Implemented by the component user; must implement `Provenanceable`.

### `type/AbstractResource`

Abstract class representing a system resource. Implemented by the component user; must implement `Provenanceable`.

### `type/AbstractContext`

Marker interface for user-provided context. Passed to every event handler alongside `ManagerContext`. Treated as a black box by the component — the implementation must be compatible with the chosen `AbstractProcess` and `AbstractResource` implementations.

### `event/Type`

Interface representing the type of a provenance event. Declares a single method: `String name()`, which all Java enums satisfy automatically.

Two built-in implementations are provided:

- **`event/ProcessType`** — `CONTROL`, `CREATE`, `CREATE_SYNTHETIC`, `EXIT`, `SIGNAL`, `UPDATE`
- **`event/ResourceType`** — `ACCESS`, `CLOSE`, `CREATE`, `DELETE`, `UPDATE`

Additional event categories can be introduced by creating a new enum that implements `Type`, without modifying existing code.

### `event/ID`

A `long`-valued identifier for an event. Implements `Comparable<ID>`, `equals`, and `hashCode`.

### `event/Event`

Abstract base class for all provenance structures. Implements `Provenanceable` (the user subclass supplies annotations). Holds an `ID` and a `Type`.

Declares the abstract method:

```
List<ProvenanceElement> handle(AbstractContext context, ManagerContext managerContext)
```

Each concrete subclass implements `handle` to produce an ordered list of `ProvenanceElement` instances (vertices and edges) using the generators in `ManagerContext`.

#### Process event types (`event/type/process/`)

| Class | Participants | Elements emitted (in order) |
|---|---|---|
| `Control` | controller, target | vertex(controller), vertex(target), edge(controller→target) |
| `Create` | parent, child | vertex(parent), vertex(child), edge(child→parent) |
| `CreateSynthetic` | process | vertex(process) |
| `Exit` | process | vertex(process) |
| `Signal` | sender, receiver | vertex(sender), vertex(receiver), edge(sender→receiver) |
| `Update` | oldVersion, newVersion | vertex(old), vertex(new), edge(new→old) |

#### Resource event types (`event/type/resource/`)

| Class | Participants | Elements emitted (in order) |
|---|---|---|
| `Access` | accessor, resource | vertex(accessor), vertex(resource), edge(accessor→resource) |
| `Close` | closer, resource | vertex(closer), vertex(resource), edge(closer→resource) |
| `Create` | creator, resource | vertex(creator), vertex(resource), edge(resource→creator) |
| `Delete` | deleter, resource | vertex(deleter), vertex(resource), edge(deleter→resource) |
| `Update` | updater, oldVersion, newVersion | vertex(updater), vertex(old), vertex(new), edge(updater→new), edge(new→old) |

All event subclasses remain **abstract** — the user subclass provides `getKeyAnnotations()` and `getExtraAnnotations()`.

### `ProvenanceElement`

A union type that holds either an `AbstractVertex` or an `AbstractEdge`. Constructed via the static factory methods `ProvenanceElement.of(AbstractVertex)` and `ProvenanceElement.of(AbstractEdge)`. Use `isVertex()` / `isEdge()` to discriminate, and `asVertex()` / `asEdge()` to unwrap.

### `VertexGenerator`

Interface with a single method:

```
AbstractVertex generate()
```

The implementation decides the concrete vertex type. The event handler is responsible for populating annotations from the participant's `Provenanceable` methods after the vertex is created.

### `EdgeGenerator`

Interface with a single method:

```
AbstractEdge generate(AbstractVertex child, AbstractVertex parent)
```

The implementation decides the concrete edge type. The event handler populates annotations from the event's `Provenanceable` methods after the edge is created.

### `ManagerContext`

Immutable holder for `VertexGenerator` and `EdgeGenerator`. Created internally by `Manager` and passed to every `Event.handle` call.

### `Manager`

The component entry point. Constructed with immutable arguments:

- `VertexGenerator vertexGenerator`
- `EdgeGenerator edgeGenerator`
- `AbstractContext context`

Exposes a single method:

```
List<ProvenanceElement> handle(List<Event> events)
```

Iterates over the event list, delegates to each event's `handle(context, managerContext)`, and returns a flat, ordered, unmodifiable list of all resulting `ProvenanceElement` instances.

## TODO

Add channel for incoming events, and channel for outgoing provenance elements. Ask/think of implementation.
Why? It would enable asynchronous event handling.