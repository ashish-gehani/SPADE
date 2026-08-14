# Codec

Each subpackage here is a separate codec for a graph - a self-contained way of encoding
a graph into some other representation and decoding it back.

Codecs:

- [`tokenize`](tokenize/README.md)
- [`uncompressed`](uncompressed/README.md)

## Adding a new codec

This package defines the shared shape every codec follows: an abstract `Graph`
(the codec-specific compressed representation) whose static `fromJSON(node)`
dispatches by the node's own `type` field, an abstract `Compress` and an abstract
`Decompress` (each holding a `Properties`) whose static `create(properties)`
dispatches by `properties.getType()`, a `Properties` class carrying a `Type`, and
the `Type` enum itself.

A new codec follows `tokenize` as its reference implementation:

- Add a value for the new codec to `Type`.
- Create a subpackage named after the codec, alongside `tokenize`.
- In it, define a `Graph` that extends this package's `Graph`, passing its `Type` to
  the superclass constructor. Its `toJSON()` should build its node with the
  superclass's `newNode()` (which stamps the `type` field) rather than a bare
  `ObjectNode`, and its `fromJSON(node)` should be a static method that reads back
  whatever the codec nested under that wrapper. This keeps every codec's JSON
  self-describing, so `Graph.fromJSON(node)` can dispatch to the right codec purely
  from the node's own `type` field, without the caller needing to pass a `Properties`.
- Optionally define a `Properties` that extends this package's `Properties`, if the
  codec needs configuration beyond just its `Type`.
- Define a `Compress` that extends this package's `Compress`, taking a `Properties`
  and producing the codec's own `Graph`.
- Define a `Decompress` that extends this package's `Decompress`, taking a
  `Properties` and consuming this package's `Graph`, narrowing it to the codec's own
  `Graph` internally.
- Wire the new codec into this package's `Compress.create` and `Decompress.create`
  switches, alongside the `tokenize` case.
- Wire the new codec's `Graph.fromJSON` into this package's `Graph.fromJSON` switch,
  alongside the `tokenize` case.
- Add a README for the new subpackage describing the codec, and link it above.
