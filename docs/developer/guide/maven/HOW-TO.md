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

## Skip a specific module

Pass the module's skip flag on the command line. Command-line `-D` properties override profile properties, so this works even when the platform profile is active. See [CHECK.md](CHECK.md) for the full flag table.

```bash
mvn compile -Dspade.skip.mac.fuse=true
```
