# spade.reporter.audit.linux.audit.event.record

Abstract `Record` base class, concrete record subclasses, the `Type` enum,
`MalformedRecordException`, and the record `Factory`.

## Record

Abstract base class for a single audit log line. Each instance holds:
- `eventId` (`ID`) and `time` (`Timestamp`) — extracted from the line header
- `type` (`Type`) — the record's audit type
- `rawRecord` (`String`) — the full original line

Contains an inner abstract `Creator` with:
- `validate(Header)` — returns `null` if the header is valid, or an error string
- `matches(Header)` — returns `true` iff `validate` returns `null`
- `create(Header)` — constructs the `Record` subclass

## Type

Enumerates all Linux audit record types:
`DAEMON_START`, `SYSCALL`, `CWD`, `PATH`, `EXECVE`, `FD_PAIR`, `SOCKADDR`, `MMAP`,
`IPC`, `MQ_SENDRECV`, `UBSI_ENTRY`, `UBSI_EXIT`, `UBSI_DEP`, `UBSI_RAW`,
`NETFILTER_HOOK`, `SOCKETCALL`, `EOE`, `PROCTITLE`, `NETIO`, `NAMESPACE`,
`NETFILTER`, `UNKNOWN`.

Each constant carries an `auditName` field matching the string in raw audit logs.
`Type.fromAuditName(String)` performs lookup via a static `HashMap`; unrecognised
types map to `UNKNOWN`.

## Record subclasses

| Class | Audit type | Key fields |
|-------|-----------|------------|
| `DaemonStart` | `DAEMON_START` | (base fields only) |
| `Syscall` | `SYSCALL` | `syscall`, `success`, `exit`, `a0`–`a3`, `items`, `exe`, `ProcessInfo` |
| `Cwd` | `CWD` | `cwd` |
| `Execve` | `EXECVE` | `argc`, args map (`a0`, `a1`, …) |
| `FdPair` | `FD_PAIR` | `fd0`, `fd1` |
| `Sockaddr` | `SOCKADDR` | `saddr` |
| `Mmap` | `MMAP` | `fd`, `flags` |
| `Ipc` | `IPC` | `ouid`, `ogid`, `mode` |
| `MqSendRecv` | `MQ_SENDRECV` | `mqdes`, `msgLen`, `msgPrio`, `absTimeoutSec`, `absTimeoutNsec` |
| `Netio` | `NETIO` | network I/O fields |
| `Netfilter` | `NETFILTER` | netfilter hook fields |
| `Namespace` | `NAMESPACE` | namespace fields |

Simple key-value records (`FdPair`, `Sockaddr`, `Mmap`, `Ipc`, `MqSendRecv`) are
parsed with `KeyValueParser.parseKeyValuePairs()`.

PATH records are in the `path/` sub-package; UBSI records are in the `ubsi/`
sub-package.

## MalformedRecordException

Thrown when a raw audit line cannot be parsed. Constructors:
- `MalformedRecordException(String msg)`
- `MalformedRecordException(String msg, String data)`
- `MalformedRecordException(String msg, String data, Throwable t)`

## Factory

`Factory.create(String rawLine)`:

1. Calls `Header.parse(rawLine)` to extract the `Type`, `ID`, and `Timestamp`.
2. Looks up the registered `Record.Creator` for that `Type` in a `HashMap`.
3. If no creator is registered (e.g. `EOE`, `PROCTITLE`, `UNKNOWN`), returns `null`.
4. Calls `creator.matches(header)`; returns `null` if the match fails.
5. Delegates to `creator.create(header)` and returns the result.

## Sub-packages

| Package | Contents |
|---------|----------|
| `helper/` | Parsing utilities shared by all record subclasses — see [helper/README.md](helper/README.md) |
| `path/` | `Path` record and `Nametype` enum — see [path/README.md](path/README.md) |
| `ubsi/` | `UbsiEntry`, `UbsiExit`, `UbsiDep`, `UbsiRaw` records and `Unit` data class — see [ubsi/README.md](ubsi/README.md) |
