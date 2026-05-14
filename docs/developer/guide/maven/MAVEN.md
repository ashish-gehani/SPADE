# Maven Guide

## Overview

Maven builds Java code only. Non-Java modules (C libraries, kernel modules, LLVM passes) are built by the autoconf/automake build system (`configure` + `make`). Maven is invoked by `make` — not directly by the developer for routine builds.

The single Maven POM is at `module/java/pom.xml`. It compiles all Java sources and produces `lib/spade.jar`. `module/java/Makefile.am` drives it:

```
make          # runs ./configure then make, which calls mvn compile
```

For common Maven commands see [HOW-TO.md](HOW-TO.md).

## Responsibilities

Maven has one responsibility:

**Java compilation** — `module/java/pom.xml` compiles all Java sources under `src/` and produces `lib/spade.jar`.

The native module build (formerly coordinated by Maven via `maven-antrun-plugin`) is now owned by `configure` + `make`. Each native module has its own `configure.ac` and `Makefile.am` instead of a `pom.xml`.

## Make Integration

`module/java/Makefile.am` invokes Maven with two overrides so that paths resolve correctly regardless of where `mvn` is run from:

```
mvn -Dspade.root=<SPADE_ROOT> -Dspade.lib.dir=<build-dir> compile
```

- `-Dspade.root` overrides `${maven.multiModuleProjectDirectory}` so all `spade.*` paths resolve to the SPADE project root.
- `-Dspade.lib.dir` redirects the output jar into the local `build/` directory; `make install` then copies it to the final destination.

## The POM

`module/java/pom.xml` owns:

- All Java dependencies (resolved from Maven Central and the project-local repository at `lib/`).
- Shared properties (`spade.root`, `spade.build.dir`, `spade.src.dir`, etc.) and `javac.options`.
- `<pluginManagement>` — plugin versions and the `maven-dependency-plugin:build-classpath` execution.
- Java compilation via `maven-compiler-plugin`, `maven-jar-plugin`, and `maven-clean-plugin`.

### Shared properties

```xml
<javac>${java.home}/bin/javac</javac>
<cc>/usr/bin/cc</cc>
<jar>${java.home}/bin/jar</jar>

<spade.root>${maven.multiModuleProjectDirectory}</spade.root>
<spade.build.dir>${spade.root}/build</spade.build.dir>
<spade.lib.dir>${spade.root}/lib</spade.lib.dir>
<spade.src.dir>${spade.root}/src</spade.src.dir>
<spade.bin.dir>${spade.root}/bin</spade.bin.dir>
<spade.jar>${spade.lib.dir}/spade.jar</spade.jar>

<!-- override via -Djavac.user.options=... -->
<javac.user.options></javac.user.options>
<javac.options>${javac.user.options} -Xlint:none -proc:none -cp ${spade.build.dir}:${spade.javac.cp}</javac.options>
```

`spade.root` resolves to the project root when overridden via `-Dspade.root` (as `make` does). `spade.javac.cp` is populated at build time by `maven-dependency-plugin:build-classpath` during the `initialize` phase.

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
