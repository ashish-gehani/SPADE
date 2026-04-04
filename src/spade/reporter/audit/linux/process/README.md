# process

The top-level model of a Linux process as tracked by the audit reporter. Combines an identity key, a full mutable state record, and a process table, with sub-packages covering each aspect of state in detail.

## Classes

### `ID`
The process key: a typed wrapper around the host-level PID string. Implements `Indexable<ID>` so it can index a `statetable.Table`. Equality and ordering are solely by PID.

### `State`
The complete mutable state of one live process. Extends `statetable.State<ID>`. Holds:

| Field | Type | Notes |
|---|---|---|
| `namespace` | `linux.namespace.Tuple` | Current namespace set; mutable via `setNamespace()` |
| `cred` | `credential.Tuple` | Current uid/gid/pid credentials; mutable via `setCred()` |
| `info` | `info.Info` | Static exec-time metadata (name, cwd, exe, …); immutable after construction |
| `fdTable` | `fd.Table` | File descriptor table; immutable reference, entries managed by the table itself |
| `memoryState` | `memory.State` | Address-space sharing status; immutable reference |
| `unitState` | `unit.State` | Active BEEP unit tracking; always present, starts inactive |

**History:** `namespace`, `user`, and `group` changes are recorded with timestamps so that provenance queries can retrieve the value closest to any audit event time (`getHistoricalNamespace`, `getHistoricalUser`, `getHistoricalGroup`). Histories are seeded from the process start time at construction.

### `Table`
The process table for the audit reporter session. Extends `statetable.Table<ID, State>` — a map from `ID` to `State` covering all currently tracked processes.

### `Context`
Extends `core.process.Context<ID, State>`. Wraps a `Table` to provide the current execution context when dispatching audit events to handlers.

## Subdirectories

| Directory | Purpose |
|---|---|
| [credential/](credential/) | Process (`pid`/`ppid`/`pgid`/`sid`), user (`uid`/`euid`/…), and group (`gid`/`egid`/…) credential value types |
| [fd/](fd/) | File descriptor table: `Num`, `OpenMode`, `State`, `Table`, and typed descriptor payloads |
| [info/](info/) | Static process metadata: `Info`, `Path`, `Time`, `TimeType` |
| [memory/](memory/) | Address-space sharing status |
| [unit/](unit/) | BEEP provenance-unit lifecycle: `Unit` identity and `State` tracking |
