# Project Structure

SPADE is structured as a top-level package, `spade`, with subpackages.

- The top-level package is `spade` — the root `configure.ac`/`Makefile.am`, which orchestrates the whole build.
- `subpackage/` contains every subpackage. See [`subpackage/README.md`](../../../subpackage/README.md) for the guidelines every subpackage follows.

```
subpackage/
├── java/            # Java build (always configured)
├── android/         # Android build (not currently wired into the root build)
├── linux/           # groups Linux-specific subpackages (audit_bridge, fuse, llvm, kernel_modules)
├── mac/             # groups macOS-specific subpackages (fuse, llvm, openbsm_bridge)
└── shared/          # source shared between subpackages via symlinks
```

## Package-level directories

At the project root, `bin/`, `lib/`, and `include/` are the general locations subpackages generate their artifacts into:

- `bin/` — built binaries (e.g. `spadeAuditBridge`, `spadeOpenBSM`).
- `lib/` — built libraries and jars (e.g. `spade.jar`, `android-spade.jar`, `libLinuxFUSE.so`, kernel modules).
- `include/` — generated headers (e.g. Java JNI headers).
