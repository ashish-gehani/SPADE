# Conventions

## Build directories

All module build output goes into the SPADE project tree. Build directory variables are derived from `SPADE_ROOT` in root `configure.ac` and exported to submodules — no module defines its own local build path.

## clean-local

`clean-local` must delete only the generated files inside the build directory, not the build directory itself. Build directory variables (e.g. `JAVA_BUILD_DIR`, `FUSE_BUILD_DIR`) are derived from `SPADE_ROOT` in root `configure.ac` and point into the live project tree, so removing the directory would destroy paths the module did not create.
