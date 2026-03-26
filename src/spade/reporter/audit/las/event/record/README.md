# spade.reporter.audit.las.event.record

Concrete record subclasses, the record type enum, parsing utilities, and the record factory.

## Record Type Enum

`Type.java` enumerates all Linux audit record types:
`DAEMON_START`, `SYSCALL`, `CWD`, `PATH`, `EXECVE`, `FD_PAIR`, `SOCKADDR`, `MMAP`,
`IPC`, `MQ_SENDRECV`, `UBSI_ENTRY`, `UBSI_EXIT`, `UBSI_DEP`, `UBSI_RAW`,
`NETFILTER_HOOK`, `SOCKETCALL`, `EOE`, `PROCTITLE`, `NETIO`, `NAMESPACE`,
`NETFILTER`, `UNKNOWN`.

Each enum constant has an `auditName` field matching the string in raw audit logs.
`Type.fromAuditName(String)` performs lookup via a static HashMap; unrecognized
types starting with `UNKNOWN[` map to `UNKNOWN`.

## Record Subclasses

| Class | Audit Type | Key Fields |
|-------|-----------|------------|
| `DaemonStart` | DAEMON_START | (base fields only) |
| `Syscall` | SYSCALL | syscall, success, exit, args (a0–a3), items, exe, `ProcessInfo` |
| `Cwd` | CWD | cwd |
| `Path` | PATH | itemNumber, name, mode, nametype, inode — see [path/](path/) |
| `Execve` | EXECVE | argc, args map (a0, a1, …) |
| `FdPair` | FD_PAIR | fd0, fd1 |
| `Sockaddr` | SOCKADDR | saddr |
| `Mmap` | MMAP | fd, flags |
| `Ipc` | IPC | ouid, ogid, mode |
| `MqSendRecv` | MQ_SENDRECV | mqdes, msgLen, msgPrio, absTimeoutSec, absTimeoutNsec |
| `Netio` | NETIO | network I/O fields |
| `Netfilter` | NETFILTER | netfilter hook fields |
| `Namespace` | NAMESPACE | namespace fields |
| `UbsiEntry` | UBSI_ENTRY | `Unit unit`, `ProcessInfo processInfo`, exe |
| `UbsiExit` | UBSI_EXIT | `ProcessInfo processInfo`, exe |
| `UbsiDep` | UBSI_DEP | `Unit writingUnit`, `Unit readingUnit`, `ProcessInfo processInfo`, exe |
| `UbsiRaw` | UBSI_RAW | syscall-like fields |

Simple records (`FdPair`, `Sockaddr`, `Mmap`, `Ipc`, `MqSendRecv`) are parsed with
`KeyValueParser.parseKeyValuePairs()`.

## MalformedRecordException

`MalformedRecordException.java` is thrown when an audit record line cannot be parsed.

Constructors:
- `MalformedRecordException(String msg)`
- `MalformedRecordException(String msg, String data)`
- `MalformedRecordException(String msg, String data, Throwable t)`

## Factory

`Factory.create(String rawLine)` parses the header, looks up the record type, and
delegates to the appropriate subclass constructor. Returns `null` for EOE, PROCTITLE,
and UNKNOWN types (skipped during processing).

## Sub-packages

| Package | Contents |
|---------|----------|
| `helper/` | Parsing utilities shared across all record subclasses — see [helper/README.md](helper/README.md) |
| `path/` | `Path` record and `Nametype` enum — see [path/README.md](path/README.md) |
| `ubsi/` | `UbsiEntry`, `UbsiExit`, `UbsiDep`, `UbsiRaw` records and `Unit` data class |
