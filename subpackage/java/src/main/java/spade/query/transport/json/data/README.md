# Data

Each subpackage here is a separate kind of payload that a `Message` can carry as its
`data` - a self-contained way of representing that payload as JSON and reading it
back.

Kinds:

- [`graph`](graph) - a graph, compressed by one of `codec`'s codecs.
- [`text`](text) - a plain string.
- [`error`](error) - a thrown exception's cause chain, reduced to one message and
  origin line per level.

## Adding a new kind of Data

This package defines the shared shape every kind follows: an abstract `Data` whose
static `fromJSON(node)` dispatches by the node's own `type` field, and the `Type`
enum itself.

A new kind follows `text` as its reference implementation:

- Add a value for the new kind to `Type`.
- Create a subpackage named after the kind, alongside `graph` and `text`.
- In it, define a class that extends this package's `Data`, passing its `Type` to
  the superclass constructor. Its `toJSON()` should build its node with the
  superclass's `newNode()` (which stamps the `type` field) rather than a bare
  `ObjectNode`, and its `fromJSON(node)` should be a static method that reads back
  whatever it nested under that wrapper. This keeps every kind's JSON
  self-describing, so `Data.fromJSON(node)` can dispatch to the right kind purely
  from the node's own `type` field.
- Wire the new kind's `fromJSON` into this package's `Data.fromJSON` switch,
  alongside the `graph` and `text` cases.
