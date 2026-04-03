# statetable

A generic keyed-state table. Three classes form the pattern:

- **`Indexable<T>`** — contract for keys. Requires `compareTo`, `equals`, and `hashCode`. Implement this on any type used as a table key.
- **`State<T>`** — base class for values. Holds a non-null `id` of type `T` (the key associated with this state entry). Extend to add domain-specific fields.
- **`Table<T, S>`** — the table itself. Maps `T` keys to `S` states in an internal `HashMap`. Provides `put`, `get`, `remove`, `contains`, `ids`, and `size`. Extend to add typed or domain-specific behaviour.

## Usage

1. Implement `Indexable<T>` on the key type.
2. Extend `State<T>` to carry the state associated with each key.
3. Extend `Table<T, S>` to get a typed table for that key/state pair.

## Example

```
fd package:
  Num        implements Indexable<Num>
  State      extends    statetable.State<Num>   (adds openState: OpenState)
  Table      extends    statetable.Table<Num, State>
```
