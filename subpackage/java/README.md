# Java

This build follows the general build guidelines of a subpackage, except for the following variations.

Since Maven is used, the `all` make target calls Maven's `package` goal. The reason is that the `package` goal builds the jar if it is not already present, and puts the resulting jar and headers to the specified location.

`src` needs to be here so that subpackages are self-contained, and for easier development environment configuration.

## `cfg` symlink

`cfg` in this directory is a symlink to `../../cfg` (the repo root's config directory). `mvn test` forks the test JVM with its working directory set to this module's basedir (here), but `spade.core.Settings` and the shipped config files resolve paths like `cfg/spade.core.Kernel.config` relative to the process's working directory, not the repo root. The symlink lets tests that validate a real shipped config (e.g. `spade.utility.mcp.server.MainTest`) find it without the JVM's working directory actually being the repo root.
