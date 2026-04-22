# spade.reporter.audit.linux.audit.event.record.ubsi

Record subclasses for UBSI (Unit-Based Syscall Interception) audit record types,
plus the `Unit` data class shared by those records.

## Unit

Data class holding the fields of a UBSI unit block:

| Field | Source key | Description |
|-------|-----------|-------------|
| `pid` | `pid` | Process ID of the unit's thread |
| `threadStartTime` | `thread_time` | Timestamp at which the thread started |
| `id` | `unitid` | Unit identifier |
| `iteration` | `iteration` | Iteration number of the unit |
| `time` | `time` | Timestamp of the unit |
| `count` | `count` | Count value of the unit |

`Unit.parse(String data, String unitKey)` extracts the block
`unitKey=(pid=X thread_time=Y unitid=Z iteration=W time=T count=C)` from `data`
using `StringHelper`, then reads each field via `substringBetween`/`substringAfter`.
Throws `MalformedRecordException` listing all missing fields.

## Record Subclasses

| Class | Audit type | Key fields |
|-------|-----------|------------|
| `UbsiEntry` | `UBSI_ENTRY` | `unit` (`Unit`), `processInfo` (`ProcessInfo`), `exe` |
| `UbsiExit` | `UBSI_EXIT` | `processInfo` (`ProcessInfo`), `exe` |
| `UbsiDep` | `UBSI_DEP` | `writingUnit` (`Unit`), `readingUnit` (`Unit`), `processInfo` (`ProcessInfo`), `exe` |
| `UbsiRaw` | `UBSI_RAW` | `syscall`, `success`, `exit`, `a0`–`a3`, `items`, `processInfo` |

### UbsiEntry

Marks the start of a new unit execution. Parses a single `unit=(…)` block, then
extracts `ProcessInfo` and `exe` from the remaining key-value pairs.

### UbsiExit

Marks the end of a unit execution. Contains only `ProcessInfo` and `exe`; no unit
block.

### UbsiDep

Represents a dependency between two units. Parses two unit blocks:
- `dep=(…)` → `writingUnit` (the unit that wrote)
- `unit=(…)` → `readingUnit` (the unit that read)

Then extracts `ProcessInfo` and `exe` from the trailing key-value pairs.

### UbsiRaw

Parsed from a `USER` record that contains a `ubsi_intercepted="…"` sub-record.
Extracts syscall-like fields (`syscall`, `success`, `exit`, `a0`–`a3`, `items`)
and `ProcessInfo` from within the quoted sub-string.

Each subclass contains a `Creator` inner class registered with `record.Factory`
that validates the header type (and, for `UbsiRaw`, the presence of
`ubsi_intercepted=`) before constructing the record.
