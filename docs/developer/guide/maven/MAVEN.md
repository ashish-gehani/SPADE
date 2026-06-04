# Maven Guide

## Overview

Maven builds Java code only. Non-Java modules (C libraries, kernel modules, LLVM passes) are built by the autoconf/automake build system (`configure` + `make`). Maven is invoked by `make` — not directly by the developer for routine builds.

The single Maven POM is at `module/java/pom.xml`. It compiles all Java sources and produces `lib/spade.jar`. `module/java/Makefile.am` drives it:

```
make          # runs ./configure then make, which calls mvn package
```

For common Maven commands see [HOW-TO.md](HOW-TO.md).

## Responsibilities

Maven has one responsibility:

**Java compilation** — `module/java/pom.xml` compiles all Java sources under `src/` and produces `lib/spade.jar`.

The native module build (formerly coordinated by Maven via `maven-antrun-plugin`) is now owned by `configure` + `make`. Each native module has its own `configure.ac` and `Makefile.am` instead of a `pom.xml`.

## Make Integration

`module/java/Makefile.am` invokes Maven with one override so that paths resolve correctly regardless of where `mvn` is run from:

```
mvn -Dspade.root=<SPADE_ROOT> package
```

- `-Dspade.root` overrides the default `${project.basedir}/../../` so all `spade.*` paths resolve to the SPADE project root.

## The POM

`module/java/pom.xml` owns:

- All Java dependencies (resolved from Maven Central and the project-local repository at `lib/`).
- Shared properties (`spade.root`, `spade.build.dir`, `spade.src.dir`, etc.).
- `<pluginManagement>` — plugin version pinning.
- Java compilation via `maven-compiler-plugin`, `maven-jar-plugin`, and `maven-clean-plugin`.

### Shared properties

```xml
<spade.root>${project.basedir}/../../</spade.root>
<spade.src.dir>${spade.root}/src</spade.src.dir>
<spade.src.resource.dir>${spade.src.dir}/resources</spade.src.resource.dir>
<spade.build.dir>${spade.root}/build</spade.build.dir>
<spade.build.native.include.dir>${spade.build.dir}/native/include</spade.build.native.include.dir>
<spade.lib.dir>${spade.root}/lib</spade.lib.dir>
```

`spade.root` defaults to the project root relative to `module/java/`. Override it via `-Dspade.root` (as `make` does) when invoking Maven from a different directory.

## Adding a Local JAR Dependency

Use this when you have a JAR that is not available on Maven Central and needs to be bundled with the project under `lib/`.

1. **Deploy the JAR to the project-local Maven repository** at `lib/`:

   ```bash
   mvn deploy:deploy-file \
     -Durl=file:///path/to/spade/lib \
     -Dfile=path/to/your.jar \
     -DgroupId=local \
     -DartifactId=<artifactId> \
     -Dversion=1.0 \
     -Dpackaging=jar
   ```

2. **Add the dependency to `module/java/pom.xml`**:

   ```xml
   <dependency>
     <groupId>local</groupId>
     <artifactId>libmything</artifactId>
     <version>1.0</version>
   </dependency>
   ```

3. **Force Maven to re-resolve** if it cached a previous failed lookup:

   ```bash
   mvn dependency:resolve -U -DincludeArtifactIds=<artifactId>
   ```

## Build Lifecycle Pattern (for future non-Java modules)

> No non-Java modules use Maven currently. This pattern is preserved for when native modules are added back under Maven.

Each non-Java module follows a uniform three-execution pattern using `maven-antrun-plugin`:

| Execution id | Maven phase | What it does |
|---|---|---|
| `check-*` | `validate` | Runs `bin/build/<platform>/<module>/check.sh`; sets a `<module>.skip` property. See [CHECK.md](CHECK.md). |
| `compile-*` | `compile` | Skipped if `<module>.skip` is `true`; otherwise runs `<ant antfile="build.xml" target="compile"/>`. See [COMPILE.md](COMPILE.md). |
| `clean-*` | `clean` | Always runs `<ant antfile="build.xml" target="clean"/>`. See [CLEAN.md](CLEAN.md). |

## Adding a Non-Java Module (future)

> The check/compile/clean scripts in `bin/build/util/check` are still present and ready to use.

1. Create `module/<platform>/<name>/pom.xml` declaring `module/java/pom.xml` (or a platform pom) as parent.
2. Define only module-specific properties. Shared paths come from the root via inheritance.
3. Follow [CHECK.md](CHECK.md), [COMPILE.md](COMPILE.md), and [CLEAN.md](CLEAN.md) to create the scripts, `build.xml`, and POM executions.
4. Register the new POM in the parent's `<modules>` list.
