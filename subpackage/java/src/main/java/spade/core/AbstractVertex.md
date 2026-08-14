# Why AbstractVertex has two kinds

## What a content vertex is, on its own

A content vertex exists to compute its hash based on its contents. That's it. Its
annotations are held in a `TreeMap<String,String>`, and its hash (`bigHashCode()`) is
derived from those annotations every time it's asked for — so identity is entirely a
function of content, and identical content always means identical hash. This was the
original functionality — the historical starting point before anything else was added to
`AbstractVertex`.

Both kinds of vertex are just `AbstractVertex` — there's no subclass split between them.
What distinguishes a content vertex is a single field: `bigHashCode`, a `final String` that
is `null` for a content vertex. `bigHashCode()` (the method) checks that field, and when it's
`null`, computes and returns the hash of the `annotations` map instead.

## The new need

At some point there was a need to create a vertex whose contents were not known — only its
identity (an id, or an already-computed hash). A content vertex can't do this: its identity
*is* its contents, so there's no way to say "this is vertex X" without supplying X's
contents.

## The reference vertex

To fulfill that need, the reference vertex was introduced. A reference vertex is given a
fixed hash directly (or an id that gets hashed) at construction, and that hash never changes
and never depends on annotations. This lets code create or refer to a vertex — for example
as an edge endpoint — before its contents are known or without ever needing to know them at
all.

A reference vertex is the same field, just non-`null`: its `bigHashCode` is set at
construction to the fixed hash, so `bigHashCode()` returns that value directly instead of
computing one. `isReferenceVertex()` is just `bigHashCode != null`. The `annotations` map is
still there on a reference vertex — annotations can still be added to it (e.g. by
`copyAsVertex()`, or an `id` annotation set from the constructor) — they're just not what the
hash is derived from.

## The uniform-handling requirement this created

Once there were two kinds of vertex, every place in the codebase that creates or reconstructs
a vertex has to handle both kinds the same way, without needing to know or care up front
which kind it's dealing with. The places where this matters:

- **Duplicating a vertex for modification** — e.g. in a filter/transformer. Filters like
  `ConvertTime` and `DropKeys`, and `AbstractTransformer` itself, call `copyAsVertex()` to
  get a mutable copy before changing annotations. The copy has to come out as the same kind
  as the original (reference stays reference, content stays content), or a reference
  vertex's fixed identity would silently turn into a content-derived one, or vice versa.
- **Storing a vertex in storage.** Whatever kind a vertex was at creation, its persisted hash
  is what storage keys off of.
- **Materializing a vertex** — rebuilding it from a stored row during query/export. This
  reconstruction can't tell, and doesn't need to tell, whether the original vertex was a
  content vertex or a reference vertex; it just takes the persisted hash and treats the
  rebuilt vertex as a reference vertex either way.
- **Passing a materialized vertex to a transformer for possible modification.** After a
  vertex is materialized (rebuilt from storage), it can optionally be handed to a
  transformer, which duplicates it via `copyAsVertex()` (e.g.
  `AbstractTransformer.createNewWithoutAnnotations()`) before applying its modification
  logic — the same duplication step described above, just applied to a reconstructed vertex
  rather than a freshly created one.

## Intended convention: construct through these functions, not `new` directly

Because of the above, constructing a vertex directly with `new` — whether `new Vertex()` /
`new Vertex(String)` or a `new` call on one of its subclasses (e.g. `new Agent()`, `new
Entity()`) — is meant to be discouraged everywhere except in reporters. A reporter is the one
place that actually knows, from the source data it's observing, whether a vertex should be a
reference vertex or a content vertex — everywhere else in the codebase is meant to stay
agnostic to that distinction and go through `AbstractVertex`'s own functions
(`copyAsVertex()`, etc.) instead, which is exactly what provides the uniform handling
described above.

This is the intended convention, not the current state of the codebase: direct `new`
construction is still called outside of reporters in a number of places today — e.g.
`AbstractVertex.copyAsVertex()` itself, `Graph.java`, `QueryInstructionExecutor.exportGraph()`
(the materialization path above), several filters (`Fusion`, `AddAnnotation`, `OPM2Prov`,
`GraphFinesse`, `OPM2ProvTC`), `Scaffold`/`LevelDB`, `SkeletonGraph`, and
`ProvenanceIntegration`.
