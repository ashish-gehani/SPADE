# Maven How-To

## Show active profiles

```bash
mvn help:active-profiles --non-recursive
```

## Show all profiles defined in the build

```bash
mvn help:all-profiles
```

## Manually activate a platform profile

The `linux` and `mac` profiles activate automatically by OS, but can be forced explicitly:

```bash
mvn compile '--activate-profiles=mac,!linux'
mvn compile '--activate-profiles=linux,!mac'
```

## Show all resolved skip flag values

```bash
mvn help:effective-pom --non-recursive | grep 'spade\.skip\.' | sed 's/^ *//' | sort -u
```

`--non-recursive` restricts the output to the root POM, avoiding repetition across reactor modules. Pass `--activate-profiles` to simulate a specific platform:

```bash
mvn help:effective-pom --non-recursive '--activate-profiles=mac,!linux' | grep 'spade\.skip\.' | sed 's/^ *//' | sort -u
```

Note: `help:effective-pom` reflects profile-activated property values but not `-D` system property overrides. To verify a specific `-D` override, use `help:evaluate`:

```bash
mvn help:evaluate --non-recursive -Dexpression=spade.skip.mac.fuse --quiet -DforceStdout -Dspade.skip.mac.fuse=true
```

## Dependency tree

```bash
mvn dependency:tree
```

## Classpath

```bash
mvn dependency:build-classpath --quiet -Dmdep.outputFile=/dev/stdout
```

## Build only a specific module

`--projects` selects a single module from the reactor. The platform profile must be active for platform modules to be reachable. `--also-make` also builds any upstream modules the selected one depends on:

```bash
mvn compile '--activate-profiles=mac' --projects module/mac/fuse/pom.xml
mvn compile '--activate-profiles=mac' --projects module/mac/fuse/pom.xml --also-make
```

## Install a local jar into the project repository

Dependencies with `groupId=local` in `pom.xml` are resolved from the file-based repository at `lib/` (declared as `spade-local-jar`). To add or update such a jar, run `install:install-file` with `-DlocalRepositoryPath` pointing at `lib/`:

```bash
mvn install:install-file \
  -Dfile=lib/<name>.jar \
  -DgroupId=local \
  -DartifactId=<name> \
  -Dversion=1.0 \
  -Dpackaging=jar \
  -DlocalRepositoryPath=lib
```

Replace `<name>` with the jar's base filename and the matching `artifactId` from `pom.xml` (e.g. `libprotobuf_java`). The plugin creates the standard Maven directory layout under `lib/local/<artifactId>/1.0/`.

To uninstall, delete that directory:

```bash
rm -rf lib/local/<name>/1.0
```

## Skip a specific module

Pass the module's skip flag on the command line. Command-line `-D` properties override profile properties, so this works even when the platform profile is active. See [CHECK.md](CHECK.md) for the full flag table.

```bash
mvn compile -Dspade.skip.mac.fuse=true
```
