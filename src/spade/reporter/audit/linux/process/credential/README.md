# credential

Immutable value types representing the credential state of a Linux process at a point in time.

## Classes

### `Process`
Process identity fields: `pid`, `ppid` (parent PID), `pgid` (process group ID), `sid` (session ID).

### `User`
User credential fields: `uid`, `euid` (effective), `suid` (saved-set), `fsuid` (filesystem).

### `Group`
Group credential fields: `gid`, `egid` (effective), `sgid` (saved-set), `fsgid` (filesystem).

### `Tuple`
Aggregates one `Process`, one `User`, and one `Group` into a single mutable credential snapshot for a process. Supports equality comparison across all three components.

## Design Notes

- `Process`, `User`, and `Group` are immutable; all fields are set at construction and validated non-null.
- `Tuple` is mutable — its components can be updated via setters — to allow credential changes (e.g. `setuid`, `setgid` syscalls) to be reflected without allocating a new tuple.
- All classes implement `equals`/`hashCode` for use as map keys or in equality-based comparisons.
