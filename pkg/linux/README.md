# Linux

This build follows the general build guidelines for a package collection, except for kernel modules.

Kernel modules are always enabled by default and fail at make time (rather than configure time) if they cannot be built.
