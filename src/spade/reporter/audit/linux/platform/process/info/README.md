# info

Static metadata describing a Linux process — the information that is set at exec time or derived from `/proc` and does not change on every syscall.

## Classes

### `Info`
The top-level record. Aggregates:
- `name` — process name (e.g. from `comm`)
- `cwd` — current working directory
- `root` — filesystem root (relevant under `chroot`/container namespaces)
- `exe` — path to the executable
- `time` — when the process was observed (see `Time`)
- `nsPid` — PID as seen inside the process's PID namespace

`root` is the only nullable field; all others are validated non-null at construction.

### `Path`
A typed wrapper around a single path string. Used for `cwd`, `root`, and `exe` in `Info` to distinguish them from plain `String` fields.

### `Time`
A timestamp paired with a `TimeType` indicating how it was obtained.

### `TimeType`
Enum with two values:
- `START` — time was read directly as the process start time (e.g. from `/proc/<pid>/stat`)
- `SEEN` — time is when the audit reporter first observed the process (start time was unavailable)
