# spade.reporter.audit.las.event.record.helper

Parsing utilities shared across all record subclasses.
All parsing uses `indexOf`/`substring` — no regexes.

## Classes

| Class | Description |
|-------|-------------|
| `Header` | Parses a raw audit log line into `type`, `eventId`, `time`, and `data` fields |
| `StringHelper` | Low-level string extraction: `substringBetween()` and `substringAfter()` |
| `KeyValueParser` | Parses space-separated `key=value` pairs into a `Map<String, String>` |
| `AuditStringParser` | Parses individual audit string values in quoted, hex-encoded, or parenthesized format |
| `ProcessInfo` | Data class holding process identity fields common to multiple record types |

## ProcessInfo Fields

`ProcessInfo` holds: `pid`, `ppid`, `uid`, `euid`, `suid`, `fsuid`, `gid`, `egid`,
`sgid`, `fsgid`, `comm`. Used by `Syscall`, `UbsiEntry`, `UbsiExit`,
`UbsiDep`, `Netio`, and `UBSIRaw`.
