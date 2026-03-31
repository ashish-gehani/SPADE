# spade.reporter.audit.linux.audit.event

Concrete implementation of the audit event reading pipeline for the Linux Audit
Subsystem (LAS). Bridges raw audit log lines to typed `core.event.Event` objects.

## ID

Extends `core.event.ID`. The LAS event ID is a decimal string (e.g. `"1234567890"`);
`ID.parse(String)` converts it to the underlying `long`.

## Timestamp

Holds the event timestamp as a `double` (seconds since epoch). Implements
`Comparable<Timestamp>`. `toString()` returns `Timestamp[seconds=<value>]`.

## Type

Enum of all LAS event types produced by this package, determined by the primary
record in a record group:

`SYSCALL`, `DAEMON_START`, `UBSI_ENTRY`, `UBSI_EXIT`, `UBSI_DEP`, `UBSI_RAW`,
`NETIO`, `NETFILTER`.

## Event

Abstract. Extends `core.event.Event`. Each instance holds an `ID`, a `Timestamp`,
a `Type`, and a `List<Record>` (the records that make up the event).

Contains an inner abstract `Creator` with three methods:
- `validate(List<Record>)` — returns `null` if the list is valid for this event
  type, or an error string if not
- `matches(List<Record>)` — returns `true` iff `validate` returns `null`
- `create(List<Record>)` — constructs the typed `Event`

### Event subclasses

| Class | Type | Primary record | Additional records |
|-------|------|----------------|--------------------|
| `Syscall` | `SYSCALL` | `record.Syscall` | CWD, PATH, EXECVE, FD_PAIR, SOCKADDR, MMAP, IPC, MQ_SENDRECV |
| `DaemonStart` | `DAEMON_START` | `record.DaemonStart` | — |
| `Netio` | `NETIO` | `record.Netio` | — |
| `Netfilter` | `NETFILTER` | `record.Netfilter` | — |
| `UbsiEntry` | `UBSI_ENTRY` | `record.ubsi.UbsiEntry` | — |
| `UbsiExit` | `UBSI_EXIT` | `record.ubsi.UbsiExit` | — |
| `UbsiDep` | `UBSI_DEP` | `record.ubsi.UbsiDep` | — |
| `UbsiRaw` | `UBSI_RAW` | `record.ubsi.UbsiRaw` | — |

Each subclass exposes a typed getter for its primary record (e.g.
`getSyscallRecord()`, `getDaemonStartRecord()`).

## Context

Extends `core.event.Context`. Accumulates `Record` objects that share the same
`(ID, Timestamp)` pair until `Factory` can construct a typed `Event`.

Key methods:
- `set(ID, Timestamp)` — initialize the context for a new group; rejects `null`
- `isSet()` — `true` when `id` and `timestamp` are both non-null
- `matches(ID, Timestamp)` — `true` when the group matches the given pair
- `addRecord(Record)` — appends a record; rejects `null`
- `getRecords()` — returns the accumulated list
- `reset()` — clears `id`, `timestamp`, and the record list

## Factory

Extends `core.event.Factory`. Holds an ordered `List<Event.Creator>` and iterates
it on each `create(core.event.Context)` call, returning the first `Event` whose
`Creator.matches()` accepts the record list. `Syscall.Creator` is listed first
because a syscall event may contain non-SYSCALL records alongside the SYSCALL
record.

Returns `null` if no creator matches (logs at `FINE` when `verbose` is set).
Throws `InvalidContextException` if the argument is not a `Context`.

## Reader

Extends `core.event.Reader`. Wraps an `InputStream` in a `BufferedReader` and
reads audit lines one at a time. On each `readEvent()` call:

1. Reads lines until EOF, passing each to `record.Factory.create(line)`.
2. Skips lines that produce `null` records.
3. Groups records by `(ID, Timestamp)` in a reused `Context`.
4. When a new `(ID, Timestamp)` pair arrives, flushes the current context through
   `event.Factory.create(context)`, resets, and starts the next group.
5. At EOF, flushes the last pending group (if any) and returns `null` thereafter.

`close()` closes the underlying `BufferedReader`.

## Pipeline

```
InputStream
  -> Reader reads lines one at a time
  -> record.Factory.create(line)        (per line -> Record or null)
  -> accumulated by (ID, Timestamp) in Context
  -> event.Factory.create(context)      (on group boundary -> typed Event)
```
