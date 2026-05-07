# Maven Guide

## Overview

SPADE is a polyglot project spanning Java, C, Clang, and kernel modules. Maven serves as the unified build frontend because the project is primarily Java. Non-Java modules (C libraries, kernel modules, LLVM passes, etc.) are built by Ant buildfiles that Maven invokes at the appropriate lifecycle phase via `maven-antrun-plugin`.

The Maven build has two responsibilities:

1. **Java compilation** — the root `pom.xml` compiles all Java sources and produces `lib/spade.jar`.
2. **Module coordination** — each module has its own POM that delegates its build and clean steps to a `build.xml` Ant buildfile in the module directory. The POM owns the variable definitions; the buildfile owns the build logic.

This mirrors the Make build. The key difference is that Maven enforces the structure through parent-child inheritance rather than explicit variable passing.

## Build Lifecycle Pattern

Each non-Java module follows a uniform three-execution pattern using `maven-antrun-plugin`:

| Execution id | Maven phase | What it does |
|---|---|---|
| `check-*` | `validate` | Runs `bin/build/<platform>/<module>/check.sh`; sets a `<module>.skip` property based on output. See [CHECK.md](CHECK.md). |
| `compile-*` | `compile` | Skipped if `<module>.skip` is `true`; otherwise runs `<ant antfile="build.xml" target="compile"/>`. See [COMPILE.md](COMPILE.md). |
| `clean-*` | `clean` | Always runs `<ant antfile="build.xml" target="clean"/>` (unconditional). See [CLEAN.md](CLEAN.md). |

Modules whose build produces a final artifact at a location outside `target/` add a fourth execution:

| Execution id | Maven phase | What it does |
|---|---|---|
| `install-*` | `package` | Skipped if `<module>.skip` is `true`; copies the artifact from `${project.build.directory}` to its final location. |

`package` is used rather than `install` because `install` semantically means installing to the local Maven repository (`~/.m2`), not project-local placement. `mvn compile` builds into `${project.build.directory}`; `mvn package` additionally places artifacts in their final locations.

`build.xml` is an Ant buildfile in the module's POM directory. It contains the `compile` and `clean` targets. Properties defined in the POM are available to Ant implicitly through the `<ant>` task.

## Module Hierarchy

The directory structure maps directly to the parent-child POM hierarchy:

```
pom.xml                                   (root: spade)
  module/android/pom.xml                  (spade-android)
  module/linux/pom.xml                    (spade-linux)
    module/linux/audit_bridge/pom.xml     (spade-linux-audit-bridge)
    module/linux/fuse/pom.xml             (spade-linux-fuse)
    module/linux/llvm/pom.xml             (spade-linux-llvm)
    module/linux/kernel_module/pom.xml    (spade-linux-kernel-module)
  module/mac/pom.xml                      (spade-mac)
    module/mac/openbsm/pom.xml            (spade-mac-openbsm)
    module/mac/fuse/pom.xml               (spade-mac-fuse)
    module/mac/llvm/pom.xml               (spade-mac-llvm)
```

Each child declares its parent via `<relativePath>`. `spade-linux` and `spade-mac` are pure aggregators — they have no build logic of their own, only a `<modules>` list. `spade-linux-audit-bridge` is a leaf — it builds `spadeAuditBridge` and has no sub-modules.

## Root POM

The root `pom.xml` is the single parent for the entire build. It owns:

- All Java dependencies (inherited by every child module).
- Shared properties (`spade.root`, `spade.build.dir`, `spade.src.dir`, etc.) and `javac.options`.
- `<pluginManagement>` — declares plugin versions and the `maven-dependency-plugin:build-classpath` execution used by all modules.
- Java compilation via `maven-compiler-plugin`, `maven-jar-plugin`, and `maven-clean-plugin`, each marked `<inherited>false</inherited>` so only the root performs the Java build.
- `maven-dependency-plugin` in `<build><plugins>` without `<inherited>false</inherited>`, so every child module inherits and runs it.

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
<spade.resource.dir>${spade.root}/resource</spade.resource.dir>
<spade.jar>${spade.lib.dir}/spade.jar</spade.jar>

<!-- override via -Djavac.user.options=... -->
<javac.user.options></javac.user.options>
<javac.options>${javac.user.options} -Xlint:none -proc:none -cp ${spade.build.dir}:${spade.javac.cp}</javac.options>
```

`spade.root` always resolves to the project root regardless of which module is being built. All module buildfiles receive paths derived from these properties.

### javac.options and spade.javac.cp

`javac.options` includes `${spade.javac.cp}`, which is populated at build time by `maven-dependency-plugin:build-classpath` during the `initialize` phase. Because the plugin is in `<build><plugins>` without `<inherited>false</inherited>`, it runs in every module's lifecycle. Child modules that pass `javac.options` to a build script therefore receive a fully resolved classpath that includes all inherited dependencies.

## Module Inclusion

All platform modules (`spade-android`, `spade-linux`, `spade-mac`) are listed unconditionally in the root `<modules>`. Whether a module's build actually runs is determined by its `check.sh` script at the `validate` phase, not by Maven profiles.

## Module Poms

Each module POM follows the same pattern:

1. Declares `<parent>` pointing to the nearest ancestor (platform pom or root pom).
2. Defines only the properties it needs — paths to its source files, output files, and any flags. Shared paths (`spade.src.dir`, `spade.bin.dir`, etc.) come from the root via inheritance.
3. Uses `maven-antrun-plugin` with three executions: `check-*` (validate), `compile-*` (compile), `clean-*` (clean). See [CHECK.md](CHECK.md) and [CLEAN.md](CLEAN.md) for the full execution patterns.

Properties local to a module are declared in that POM only. Do not add them to the root.

## Adding a Local JAR Dependency

Use this when you have a JAR that is not available on Maven Central and needs to be
bundled with the project under `lib/`.

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

   Replace `<artifactId>` with a descriptive name (e.g. `libmything`). Use `groupId=local`
   to stay consistent with the other local JARs.

2. **Add the dependency to `pom.xml`**:

   ```xml
   <dependency>
     <groupId>local</groupId>
     <artifactId>libmything</artifactId>
     <version>1.0</version>
   </dependency>
   ```

3. (OPTIONAL) **Add the JAR to `cfg/java.classpath`** so the runtime launch script (`bin/spade`)
   picks it up. Append a line using the flat filename you copied to `lib/`:

   ```
   lib/your.jar
   ```

   Note: the deploy step in (1) places the JAR inside `lib/local/<artifactId>/1.0/` for
   Maven resolution. The classpath entry points to the original flat copy in `lib/` used
   by the runtime script — both are needed.

4. **Force Maven to re-resolve** if it cached a previous failed lookup:

   ```bash
   mvn dependency:resolve -U -DincludeArtifactIds=<artifactId>
   ```

## Adding a Module

1. Create the POM at the appropriate path under `module/`:

   ```
   module/<platform>/<name>/pom.xml
   ```

2. Declare the parent (the platform pom or the root pom):

   ```xml
   <parent>
     <groupId>io.github.spade</groupId>
     <artifactId>spade-linux</artifactId>
     <version>${revision}</version>
     <relativePath>../pom.xml</relativePath>
   </parent>
   ```

3. Define only module-specific properties. Use inherited shared properties for everything else.

4. Follow [CHECK.md](CHECK.md), [COMPILE.md](COMPILE.md), and [CLEAN.md](CLEAN.md) to create the scripts, `build.xml`, and POM executions.

5. Register the new POM in the parent's `<modules>` list.
