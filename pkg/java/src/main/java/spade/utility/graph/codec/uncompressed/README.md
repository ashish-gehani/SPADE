# Uncompressed codec

This package implements a codec that performs no compression - its `Graph` holds a
`spade.core.Graph` directly, and its JSON is every vertex and edge written out as-is,
with no tokenization or other transformation applied.

It exists as the baseline codec: the one to reach for when a graph should be carried
across the wire in its plain form, and as the reference case that any other codec's
round trip can be compared against.
