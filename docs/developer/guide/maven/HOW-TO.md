# Maven How-To

> Maven builds Java code only. The POM is at `subpackage/java/pom.xml`. Run commands from that directory, or pass `-f subpackage/java/pom.xml` from the project root. `make` handles this automatically for routine builds.

## Build with debug symbols

Pass `-Dmaven.compiler.debuglevel` to include full debug information (line numbers, local variable names, and source file names) in the compiled classes:

```bash
mvn compile -Dmaven.compiler.debug=true -Dmaven.compiler.debuglevel=lines,vars,source
```

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
