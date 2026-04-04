# memory

Tracks whether a process's virtual memory is private or shared with another process.

## Classes

### `State`
Records the memory-sharing status of a process. Constructed with no argument for a private address space, or with a process `ID` when the address space is shared (e.g. after `clone(2)` with `CLONE_VM`). `isSharedWith()` returns `true` when shared; `getSharedWith()` returns the owning process's `ID`.
