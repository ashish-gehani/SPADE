# fd

Models the file descriptor table of a Linux process: the numeric fd, what it is open for, what it refers to, and the table itself.

## Classes

### `Num`
A typed wrapper around the integer fd number. Implements `Indexable<Num>` so it can serve as a key in a `statetable.Table`.

### `OpenMode`
Enum recording whether the fd was opened for `READ`, `WRITE`, or `UNKNOWN` access.

### `State`
One entry in the fd table: a `Num` key, an `OpenMode`, and a `Descriptor` (from [type/](type/)) identifying what the fd refers to. Extends `statetable.State<Num>`.

### `Table`
The fd table for a process. Extends `statetable.Table<Num, State>`. Carries an optional reference to another process's `ID` when the table is shared (e.g. after `clone(2)` with `CLONE_FILES`). `isSharedWith()` returns `true` when shared; `getSharedWith()` returns the owning process's ID.

## Subdirectory

- [type/](type/) — typed descriptor payloads (`File`, `NetworkSocket`, `UnnamedPipe`, etc.)
