# Kernel Modules

This build deviates from the general build guidelines of a subpackage.

## Deviations

- In `configure`, warnings are raised rather than errors. This is to always attempt configure successfully but fail at make time if the kernel modules cannot be built.
- `clean` is not guarded by `KERNEL_MODULES`, to allow developers to clean easily during testing.
- `Makefile.am` doesn't create the build directory; `Kbuild` does. In the `all` target, the specified target is called first, and then `package`, to place the files at the package level.
- `Kbuild` uses `$(if $(M),$(M),$(CURDIR))` because it is called from two contexts, where `M` is either set or not set (kernel's kbuild system). This is not fully understood yet.
- `Kbuild` is one script that builds all kernel modules, tests, and runs tests as well. This will need to be updated in the future.
- Kernel module `clean` is not done using the kbuild system's clean, to avoid cleaning errors when kernel modules are not supported (done for developer ease).
