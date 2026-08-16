# Project Structure

SPADE is structured as a top-level package, `spade`, with packages nested under `pkg/`.

- The top-level package is `spade` — the root `configure.ac`/`Makefile.am`, which orchestrates the whole build.
- `pkg/` contains every package. See [`pkg/README.md`](../../../pkg/README.md) for the guidelines every package follows.

```
pkg/
├── java/            # Java build (always configured)
├── android/         # Android build (not currently wired into the root build)
├── linux/           # groups Linux-specific packages (audit_bridge, fuse, llvm, kernel_modules)
├── mac/             # groups macOS-specific packages (fuse, llvm, openbsm_bridge)
└── shared/          # source shared between packages via symlinks
```

## Package-level directories

At the project root, `bin/`, `lib/`, and `include/` are the general locations packages generate their artifacts into:

- `bin/` — built binaries (e.g. `spadeAuditBridge`, `spadeOpenBSM`).
- `lib/` — built libraries and jars (e.g. `spade.jar`, `android-spade.jar`, `libLinuxFUSE.so`, kernel modules).
- `include/` — generated headers (e.g. Java JNI headers).
