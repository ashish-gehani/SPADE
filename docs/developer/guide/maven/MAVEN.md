# Maven Guide

## Overview

Maven builds Java code only. Native subpackages (C libraries, kernel modules, LLVM passes) are built by the autoconf/automake build system instead — see [`MAKE.md`](../make/MAKE.md) and [`subpackage/README.md`](../../../../subpackage/README.md).

The single Maven POM is at `subpackage/java/pom.xml`. It compiles all Java sources and produces `spade.jar`. `subpackage/java/Makefile.am` drives it — see that file for exactly how `mvn` is invoked.

For common Maven commands see [HOW-TO.md](HOW-TO.md).

## Responsibilities

Maven has one responsibility: compiling Java sources under `src/` and packaging `spade.jar`, via `subpackage/java/pom.xml`.

## Make Integration

`subpackage/java/Makefile.am` invokes Maven's `package` goal with a set of `-Dspade.*` property overrides so paths resolve to the locations the top-level `configure.ac` computed. See that `Makefile.am` for the exact properties passed.

## The POM

`subpackage/java/pom.xml` owns all Java dependencies, shared `spade.*` properties, plugin version pinning, and the compile/package/clean steps (`maven-compiler-plugin`, `maven-jar-plugin`, `maven-antrun-plugin` for copying the final jar, `maven-clean-plugin`). Read the POM directly for the current property names and values rather than a duplicated list here.

## Adding a Local JAR Dependency

Use this when you have a JAR that is not available on Maven Central and needs to be bundled with the project under `lib/`.

1. Deploy the JAR to the project-local Maven repository at `lib/` with `mvn deploy:deploy-file` (see [HOW-TO.md](HOW-TO.md) for the equivalent `install:install-file` form).
2. Add the corresponding `<dependency>` to `subpackage/java/pom.xml`.
3. If Maven cached a previous failed lookup, force re-resolution with `mvn dependency:resolve -U -DincludeArtifactIds=<artifactId>`.
