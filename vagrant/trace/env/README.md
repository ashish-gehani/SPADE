# env/

Constants only, no logic. One small file per category, plus an
aggregator, `env.sh`, that sources the rest in dependency order (each file
may reference constants from a file sourced before it -- `vm.sh` must stay
first since several other files read its `ENV_VM_WORK_HOME`; the required
order is documented inline in `env.sh` itself).

Naming convention used throughout: every constant is prefixed
`ENV_<FILE>_...` (e.g. `ENV_KAFKA_SERVER_PORT` in `kafka.sh`). Within
that, `_HOME` names a thing's canonical root/base directory (after the
`JAVA_HOME`/`$HOME` convention -- `ENV_SPADE_HOME`, `ENV_VM_WORK_HOME`),
`_DIR` names a more incidental, single-purpose directory
(`ENV_KAFKA_SERVER_LOG_DIR`).

## `vm.sh`

VM-side layout -- the paths as they exist once provisioning is actually
running inside the guest. Defines `ENV_VM_HOST_SHARED_VAGRANT_DIR`
(Vagrant's default synced folder), `ENV_VM_TRACE_HOME` (a
purpose-named alias for the same path, for scripts that mean "this
project's own tree"), and `ENV_VM_WORK_HOME` (a single working directory
for everything generated/downloaded at provision time -- the SPADE
checkout, the Kafka broker install, the relocated Maven repo, downloaded
datasets -- gitignored).

## `maven.sh`

Relocates Maven's local repository under `ENV_VM_WORK_HOME` (so it
survives a `vagrant destroy`/`up` cycle instead of being re-fetched every
time, since it lives on the host-shared mount) and exports `MAVEN_OPTS`
accordingly, so every `mvn`/`make` invocation downstream honors it
automatically.

## `spade.sh`

SPADE checkout/process constants: `ENV_SPADE_HOME`, `ENV_SPADE_BIN`,
`ENV_SPADE_REPO_BRANCH`, the auto-saved control-client config path, the
current log file path, the control-port wait-attempt tuning, the
log-marker poll interval (`ENV_SPADE_DONE_MARKER_WAIT_SLEEP_SECONDS`) --
just the interval, not a total attempt count, since how long is reasonable
to wait for a marker depends on each scenario's own input size and is set
per scenario instead -- and the graceful-stop wait-attempt/interval tuning
(`ENV_SPADE_STOP_WAIT_ATTEMPTS`/`ENV_SPADE_STOP_WAIT_SLEEP_SECONDS`), after
which `spade_stop` falls back to killing the process outright.

## `kafka.sh`

Constants for the one Kafka broker shared by every scenario (only one
scenario's storage/reporter is ever attached at a time, so there's no
port conflict -- per-scenario topics are what keep scenarios apart, not
separate brokers): download URL/version, install/config paths,
`ENV_KAFKA_SERVER_PORT`, and broker-ready wait tuning.

## `datasets.sh`

External datasets used as scenario input. The DARPA TC trace's share URL
is hand-maintained; the file id used by the actual download request is
parsed out of it, so there's one source of truth instead of two constants
that could drift apart. Also defines the audit log path used by the
`audit-to-*` scenarios -- the log file itself isn't downloaded
automatically, but its directory is created (empty, ready for a user to
drop a file into) by those scenarios' own `scenario_prepare_input`.

## `util.sh`

`ENV_UTIL_AVRO_TOOLS_JAR`, built from `maven.sh`'s relocated repo path so
it automatically follows the Maven relocation above.

## `scenario.sh`

`ENV_SCENARIO_HOME` (root of every scenario's own directory),
`ENV_SCENARIO_AVAILABLE` (the list of valid scenario names -- the single
source of truth `provision.sh` validates against and prints on a typo),
`ENV_SCENARIO_SELECTED` (the one line a user edits to pick a scenario),
and `ENV_SCENARIO_SELECTED_HOME`.
