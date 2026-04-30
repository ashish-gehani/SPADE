# Maven Guide

## Overview

The Maven build has two responsibilities:

1. **Java compilation** — the root `pom.xml` compiles all Java sources and produces `lib/spade.jar`.
2. **Subcomponent coordination** — each subcomponent has its own POM that delegates its build and clean steps to scripts under `bin/`. The POM owns the variable definitions; scripts own the build logic.

This mirrors the Make build. The key difference is that Maven enforces the structure through parent-child inheritance rather than explicit variable passing.

## Module Hierarchy

The directory structure maps directly to the parent-child POM hierarchy:

```
pom.xml                         (root: spade)
  module/android/pom.xml        (spade-android)
  module/linux/pom.xml          (spade-linux)
    linux/audit/pom.xml         (spade-linux-audit)
      audit/kernel/pom.xml      (spade-linux-audit-kernel)
    linux/fuse/pom.xml          (spade-linux-fuse)
    linux/llvm/pom.xml          (spade-linux-llvm)
  module/mac/pom.xml            (spade-mac)
    mac/openbsm/pom.xml         (spade-mac-openbsm)
    mac/fuse/pom.xml            (spade-mac-fuse)
    mac/llvm/pom.xml            (spade-mac-llvm)
```

Each child declares its parent via `<relativePath>`. Platform poms (`spade-linux`, `spade-mac`) are pure aggregators — they have no build logic of their own, only a `<modules>` list.

## Root POM

The root `pom.xml` is the single parent for the entire build. It owns:

- All Java dependencies (inherited by every child module).
- Shared properties (`spade.root`, `spade.build.dir`, `spade.src.dir`, etc.) and `javac.options`.
- `<pluginManagement>` — declares plugin versions and the `maven-dependency-plugin:build-classpath` execution used by all modules.
- Java compilation via `maven-compiler-plugin`, `maven-jar-plugin`, and `maven-clean-plugin`, each marked `<inherited>false</inherited>` so only the root performs the Java build.
- `maven-dependency-plugin` in `<build><plugins>` without `<inherited>false</inherited>`, so every child module inherits and runs it.

### Shared properties

```xml
<spade.root>${maven.multiModuleProjectDirectory}</spade.root>
<spade.build.dir>${spade.root}/build</spade.build.dir>
<spade.lib.dir>${spade.root}/lib</spade.lib.dir>
<spade.src.dir>${spade.root}/src</spade.src.dir>
<spade.bin.dir>${spade.root}/bin</spade.bin.dir>

<javac.options>-Xlint:none -proc:none -cp ${spade.build.dir}:${spade.javac.cp}</javac.options>
```

`spade.root` always resolves to the project root regardless of which module is being built. All subcomponent scripts receive paths derived from these properties.

### javac.options and spade.javac.cp

`javac.options` includes `${spade.javac.cp}`, which is populated at build time by `maven-dependency-plugin:build-classpath` during the `initialize` phase. Because the plugin is in `<build><plugins>` without `<inherited>false</inherited>`, it runs in every module's lifecycle. Child modules that pass `javac.options` to a build script therefore receive a fully resolved classpath that includes all inherited dependencies.

## Platform Activation

Platform poms are included in the reactor through OS-activated profiles in the root POM:

| Profile | Activation | Modules added |
|---------|-----------|---------------|
| `linux` | OS = Linux / amd64 | `module/linux/pom.xml` |
| `mac`   | OS = macOS          | `module/mac/pom.xml`   |

`module/android/pom.xml` is always in the reactor (unconditional `<modules>` entry). Its build steps are guarded by an internal profile (see below).

## Subcomponent Poms

Each subcomponent POM follows the same pattern:

1. Declares `<parent>` pointing to the nearest ancestor (platform pom or root pom).
2. Defines only the properties it needs — paths to its source files, output files, and any flags. Shared paths (`spade.src.dir`, `spade.bin.dir`, etc.) come from the root via inheritance.
3. Uses `exec-maven-plugin` to run its `build.sh` at `compile` and its `clean.sh` at `clean`.
4. Passes all required inputs to the script as named arguments.

```xml
<properties>
  <openbsm.mac.c.src>${spade.src.dir}/spade/reporter/spadeOpenBSM.c</openbsm.mac.c.src>
  <openbsm.mac.output>${spade.lib.dir}/spadeOpenBSM</openbsm.mac.output>
</properties>

<build>
  <plugins>
    <plugin>
      <groupId>org.codehaus.mojo</groupId>
      <artifactId>exec-maven-plugin</artifactId>
      <executions>
        <execution>
          <id>build-openbsm</id>
          <phase>compile</phase>
          <goals><goal>exec</goal></goals>
          <configuration>
            <executable>${spade.bin.dir}/mac/openbsm/build.sh</executable>
            <workingDirectory>${spade.root}</workingDirectory>
            <arguments>
              <argument>--c-src</argument>  <argument>${openbsm.mac.c.src}</argument>
              <argument>--output</argument> <argument>${openbsm.mac.output}</argument>
            </arguments>
          </configuration>
        </execution>
        <execution>
          <id>clean-openbsm</id>
          <phase>clean</phase>
          ...
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

Properties local to a subcomponent are declared in that POM only. Do not add them to the root.

## Profile-Gated Build Steps

Some subcomponents are optional. Their build step is wrapped in a profile, but the clean step is always unconditional (at the top-level `<build>`, not inside the profile) so that `mvn clean` always removes artifacts regardless of whether the build was activated.

| Subcomponent | Activation |
|---|---|
| `linux`, `mac` | OS profile in root (auto) |
| `linux-fuse`, `mac-fuse` | File exists: `fuse.pc` (auto) |
| `android` | Property: `-Dandroid` |
| `kernel-modules` | Property: `-Dkernel.modules=true` |
| `llvm` | Property: `-Dllvm` |

Profile-gated example (build guarded, clean unconditional):

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.codehaus.mojo</groupId>
      <artifactId>exec-maven-plugin</artifactId>
      <executions>
        <execution>
          <id>clean-mycomponent</id>
          <phase>clean</phase>
          ...
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>

<profiles>
  <profile>
    <id>mycomponent</id>
    <activation>...</activation>
    <build>
      <plugins>
        <plugin>
          <groupId>org.codehaus.mojo</groupId>
          <artifactId>exec-maven-plugin</artifactId>
          <executions>
            <execution>
              <id>build-mycomponent</id>
              <phase>compile</phase>
              ...
            </execution>
          </executions>
        </plugin>
      </plugins>
    </build>
  </profile>
</profiles>
```

## Adding a Subcomponent

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

3. Define only subcomponent-specific properties. Use inherited shared properties for everything else.

4. Add `build-` and `clean-` executions via `exec-maven-plugin`. Put clean unconditionally in `<build>`; put build inside a profile if the subcomponent is optional.

5. Register the new POM in the parent's `<modules>` list.

6. If the subcomponent is platform-conditional and no suitable platform pom exists, add an OS-activated profile to the root POM (following the `linux` / `mac` pattern).
