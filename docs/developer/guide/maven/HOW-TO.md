# Maven How-To

> Maven builds Java code only. The POM is at `subpackage/java/pom.xml`. Run commands from that directory, or pass `-f subpackage/java/pom.xml` from the project root. `make` handles this automatically for routine builds.

## Build with debug symbols

Pass `-Dmaven.compiler.debuglevel` to include full debug information (line numbers, local variable names, and source file names) in the compiled classes:

```bash
mvn compile -Dmaven.compiler.debug=true -Dmaven.compiler.debuglevel=lines,vars,source
```

## Run tests

```bash
mvn test
```

Run a single test class with `-Dtest`:

```bash
mvn test -Dtest=SettingTest
```

Tests live under `src/test/java`, following the same package structure as `src/main/java`. The POM only declares `junit-jupiter-api`; running tests for the first time needs network access once, to pull `junit-jupiter-engine` and `junit-platform-launcher` (used to actually discover and run JUnit 5 tests) into the local/`lib` repository — after that they're cached and `-o` (offline) works.

## Dependency tree

```bash
mvn dependency:tree
```

## Classpath

```bash
mvn dependency:build-classpath --quiet -Dmdep.outputFile=/dev/stdout
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

## Force re-resolve a dependency

```bash
mvn dependency:resolve -U -DincludeArtifactIds=<artifactId>
```

## Show resolved effective POM

```bash
mvn help:effective-pom
```
