# Project Structure

Top-level directories and their purpose:

- `.github/workflows/` -- CI workflows.
- `bin/` -- scripts to build and run SPADE.
- `cfg/` -- runtime configuration.
- `db/` -- default location for database storage created at runtime.
- `docs/` -- project documentation.
- `include/` -- headers consumed by native/JNI builds; mix of generated and shipped headers.
- `lib/` -- built libraries and bundled dependencies.
- `log/` -- runtime logs created when SPADE runs.
- `m4/` -- shared autoconf macros used across the build.
- `build-aux/` -- autotools build helper scripts (generated/vendored, not hand-edited).
- `pkg/` -- SPADE's autoconf/automake packages, grouped by platform/language. See [pkg/README.md](pkg/README.md) for the structure and conventions each package follows.
- `test/` -- legacy test resources.
- `vagrant/` -- VM provisioning configs for development environments.
