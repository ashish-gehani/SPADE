# spade.reporter.audit.las.event.record.path

PATH audit record class and its nametype enum.

## Classes

| Class | Description |
|-------|-------------|
| `Path` | Record for PATH audit type; holds `itemNumber`, `name`, `mode`, `nametype`, `inode`; implements `Comparable` for ordering by item number |
| `Nametype` | Enum of path name types: `NORMAL`, `PARENT`, `CREATE`, `UPDATE`, `DELETE`, `UNKNOWN`; provides `parse()` |

## Path

`Path` is produced for each `PATH` record in a syscall event. A single syscall
event may generate multiple PATH records (one per file path argument), distinguished
by `itemNumber`.

Static utility methods:
- `parsePathType(String)` — parses the `type` of Filesystem entry type
- `parsePermissions(String)` — parses the octal `mode` field into permission bits
