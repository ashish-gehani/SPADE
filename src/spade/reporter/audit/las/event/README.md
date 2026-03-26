# spade.reporter.audit.las.event

Event subclasses and event factory.

## Event (abstract base)

All event classes extend `Event`. Fields: `eventId`, `time`, `eventType`, `records`.

| Method | Description |
|--------|-------------|
| `getEventId()` | Audit event ID shared by all records in the event |
| `getTime()` | Timestamp from the audit header |
| `getEventType()` | `Type` enum value for this event |
| `getRecords()` | Defensive copy of the record list |

## Type Enum

`Type.java` enumerates all event types:
`SYSCALL`, `DAEMON_START`, `UBSI_ENTRY`, `UBSI_EXIT`, `UBSI_DEP`, `UBSI_RAW`,
`NETIO`, `NETFILTER`.

## Event Subclasses

All subclasses live directly in `spade.reporter.audit.las.event`.

| Class | Type | Primary record |
|-------|------|----------------|
| `Syscall` | SYSCALL | `record.Syscall` + optional CWD, PATH, EXECVE, FD_PAIR, SOCKADDR, MMAP, IPC, MQ_SENDRECV |
| `DaemonStart` | DAEMON_START | `record.DaemonStart` |
| `Netio` | NETIO | `record.Netio` |
| `Netfilter` | NETFILTER | `record.Netfilter` |
| `UbsiEntry` | UBSI_ENTRY | `record.UbsiEntry` |
| `UbsiExit` | UBSI_EXIT | `record.UbsiExit` |
| `UbsiDep` | UBSI_DEP | `record.UbsiDep` |
| `UbsiRaw` | UBSI_RAW | `record.UbsiRaw` |

## MalformedEventException

Thrown when an event cannot be constructed from its records.

Constructors:
- `MalformedEventException(String msg)`
- `MalformedEventException(String msg, String eventId)`
- `MalformedEventException(String msg, String eventId, Throwable t)`

## Factory

`Factory.create(List<Record>)` inspects the record set, identifies the primary
record type, and instantiates the corresponding `Event` subclass. Returns `null`
for unrecognized record sets.

## Sub-packages

| Package | Contents |
|---------|----------|
| `channel/` | Thread-safe bounded event channel — see [channel/README.md](channel/README.md) |
| `reader/` | Reader abstraction + metrics — see [reader/README.md](reader/README.md) |
| `writer/` | Writer abstraction + file rotation — see [writer/README.md](writer/README.md) |
| `record/` | Record subclasses + factory — see [record/README.md](record/README.md) |
