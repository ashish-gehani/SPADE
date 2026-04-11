# fd/type

Typed representations of what a Linux file descriptor refers to. Each concrete class captures the fields that are meaningful for that descriptor kind.

## Class hierarchy

```
Descriptor (abstract)          — base; holds a Type tag
├── Path (abstract)            — path-based resources: path, rootFSPath, combinedPath, inode
│   ├── File
│   ├── Directory
│   ├── BlockDevice
│   ├── CharacterDevice
│   ├── Link
│   ├── NamedPipe
│   ├── UnixSocket
│   └── PosixMessageQueue
├── FdPair (abstract)          — paired fds created by a single syscall: tgid, fd0, fd1
│   ├── UnnamedPipe
│   └── UnixSocketPair
├── SystemV (abstract)         — System V IPC objects: id, ouid, ogid, ipcNamespace
│   ├── SystemVMessageQueue
│   └── SystemVSharedMemory
├── NetworkSocket              — TCP/UDP socket: network (Network), netNamespaceId
├── NetworkSocketPair          — socketpair() result: protocol only
├── Memory                     — memory-mapped region: memoryAddress, size, tgid
└── Unknown                    — unrecognized fd: tgid, fd
```

## `Type` enum

Enumerates all supported descriptor kinds and provides a human-readable `subtype` string (e.g. `Type.FILE.subtype == "file"`). Every `Descriptor` subclass sets its `type` field to the corresponding enum constant.

## Design notes

- All fields are public final and validated non-null at construction — instances are immutable.
- `Path` captures three path views (`path`, `rootFSPath`, `combinedPath`) to support container/namespace-aware path resolution alongside the raw filesystem inode.
- `FdPair` records both ends of a paired descriptor (`fd0`, `fd1`) and the owning thread-group (`tgid`), reflecting how `pipe(2)` and `socketpair(2)` produce two linked fds in one call.
- `SystemV` carries `ouid`/`ogid` (owner UID/GID) and `ipcNamespace` to distinguish IPC objects across namespaces.
- `NetworkSocketPair` is a direct `Descriptor` subclass (not `FdPair`) — it records only the `protocol` because the individual fd numbers are tracked at a higher level.
