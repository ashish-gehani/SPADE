# `spade.reporter.audit.linux.audit.event`

Typed, structured representation of Linux Audit Subsystem (LAS) events. Bridges
raw audit records (from the `record/` subpackage) to `core.event.Event` objects
consumed by the rest of the pipeline.

## Directory structure

```
event/
├── ID.java             # LAS-specific event identity (decimal string → long)
├── Timestamp.java      # Event timestamp (double seconds since epoch)
├── Type.java           # Enum of all handled LAS event types
├── Context.java        # Accumulates records sharing the same (ID, Timestamp)
├── Event.java          # Abstract base event + inner Creator contract
├── Factory.java        # Iterates creators to produce a typed Event from a Context
├── Syscall.java        # SYSCALL event (one Syscall record + optional supplementary records)
├── DaemonStart.java    # DAEMON_START event (one DaemonStart record)
├── Netio.java          # NETIO event (one Netio record)
├── Netfilter.java      # NETFILTER event (one Netfilter record)
├── UbsiEntry.java      # UBSI_ENTRY event (one UbsiEntry record)
├── UbsiExit.java       # UBSI_EXIT event (one UbsiExit record)
├── UbsiDep.java        # UBSI_DEP event (one UbsiDep record)
├── UbsiRaw.java        # UBSI_RAW event (one UbsiRaw record)
└── record/             # Parsed record types and their factory
```

## Core types

### `ID`

Extends `core.event.ID`. The LAS event ID is a decimal string (e.g. `"1234567890"`);
`ID.parse(String)` converts it to the underlying `long`.

### `Timestamp`

Holds the event timestamp as a `double` (seconds since epoch). Implements
`Comparable<Timestamp>`. `toString()` returns `Timestamp[seconds=<value>]`.

### `Type`

Enum of all LAS event types produced by this package. The type is determined
by the primary record present in a record group:

`SYSCALL`, `DAEMON_START`, `UBSI_ENTRY`, `UBSI_EXIT`, `UBSI_DEP`, `UBSI_RAW`,
`NETIO`, `NETFILTER`.

## `Context`

Extends `core.event.Context`. Accumulates `Record` objects that share the same
`(ID, Timestamp)` pair until `Factory` can construct a typed `Event`.

| Method | Description |
|---|---|
| `set(ID, Timestamp)` | Initialize the context for a new group; rejects `null` |
| `isSet()` | `true` when both `id` and `timestamp` are non-null |
| `matches(ID, Timestamp)` | `true` when the group matches the given pair |
| `addRecord(Record)` | Appends a record; rejects `null` |
| `getRecords()` | Returns the accumulated list |
| `reset()` | Clears `id`, `timestamp`, and the record list |

## `Event` (abstract)

Extends `core.event.Event`. Each instance holds an `ID`, a `Timestamp`, a
`Type`, and a `List<Record>` (the full set of records that make up the event).
`setRecords()` / `unsetRecords()` allow the record list to be populated after
construction.

### Inner abstract `Creator`

Each concrete `Event` subclass has a static `Creator` nested class:

| Method | Description |
|---|---|
| `validate(List<Record>)` | Returns `null` if the record list is valid, or an error string if not |
| `matches(List<Record>)` | Returns `true` iff `validate` returns `null` |
| `create(List<Record>)` | Constructs the typed `Event`; throws `MalformedEventException` on failure |

### Event subclasses

| Class | Type | Primary record | Additional records |
|---|---|---|---|
| `Syscall` | `SYSCALL` | `record.Syscall` | CWD, PATH, EXECVE, FD_PAIR, SOCKADDR, MMAP, IPC, MQ_SENDRECV |
| `DaemonStart` | `DAEMON_START` | `record.DaemonStart` | — |
| `Netio` | `NETIO` | `record.Netio` | — |
| `Netfilter` | `NETFILTER` | `record.Netfilter` | — |
| `UbsiEntry` | `UBSI_ENTRY` | `record.ubsi.UbsiEntry` | — |
| `UbsiExit` | `UBSI_EXIT` | `record.ubsi.UbsiExit` | — |
| `UbsiDep` | `UBSI_DEP` | `record.ubsi.UbsiDep` | — |
| `UbsiRaw` | `UBSI_RAW` | `record.ubsi.UbsiRaw` | — |

Each subclass exposes a typed getter for its primary record (e.g.
`getSyscallRecord()`, `getDaemonStartRecord()`, `getUbsiEntryRecord()`).

## `Factory`

Extends `core.event.Factory<Event, Context>`. Holds a fixed, ordered
`List<Event.Creator>` and iterates it on each `create(Context)` call, returning
the first typed `Event` whose `Creator.matches()` accepts the record list.

`Syscall.Creator` is listed first because a syscall event may contain non-SYSCALL
records (e.g. USER records) alongside the primary SYSCALL record.

Returns `null` if no creator matches (logs at `INFO` when `verbose` is enabled),
or if the record list is empty. Throws `IllegalArgumentException` if the argument
is `null`.

## Subpackages

- [`record/`](record/README.md) — parsed record types (`Syscall`, `Cwd`, `Path`, UBSI records, etc.) and the `Factory` that parses raw audit lines into `Record` objects.
