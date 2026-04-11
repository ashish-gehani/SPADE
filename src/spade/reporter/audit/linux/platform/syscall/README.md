# kernel/syscall

Types for representing and looking up Linux syscalls by number or name.

## Classes

### `Syscall`
An immutable key type identifying a single syscall. Holds `int num` (syscall number) and `String name`. Implements `Indexable<Syscall>` (ordered by `num`) so it can serve as a key in a `statetable.Table`.

### `State`
A `statetable.State<Syscall>` entry keyed by a `Syscall`.

### `Table`
A `statetable.Table<Syscall, State>` with two convenience lookup methods:
- `getFromName(String name)` — returns the `State` whose `Syscall.name` matches; throws `NoSuchSyscall` if not found.
- `getFromNum(int num)` — returns the `State` whose `Syscall.num` matches; throws `NoSuchSyscall` if not found.

### `NoSuchSyscall`
Checked exception thrown when a syscall lookup fails. Constructors:
- `NoSuchSyscall(String message)`
- `NoSuchSyscall(String message, Throwable cause)`

Static factory methods for the two common lookup failures:
- `NoSuchSyscall.forName(String name)` — for unknown syscall names.
- `NoSuchSyscall.forNum(int num)` — for unknown syscall numbers.
