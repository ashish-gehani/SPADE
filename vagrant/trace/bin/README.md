# bin/

The driver, a standalone utility script, and the shared library they're
both built from.

## `provision.sh`

The one driver for this VM, invoked by the `Vagrantfile` on `vagrant up`.
Scenario-agnostic -- it never branches on scenario name, only ever calling
the hook functions every `scenarios/<name>/scenario.sh` is required to
define.

`main` sets a `cleanup` function as an `EXIT` trap before doing anything
else, then sources `env/env.sh`, sources every file under `common/`,
validates the scenario named by `env/scenario.sh`'s `ENV_SCENARIO_SELECTED`
against that same file's `ENV_SCENARIO_AVAILABLE` (printing the valid names
on a typo), and sources the selected scenario's `scenario.sh`. It then runs
through setup (SPADE, then the Kafka broker), the scenario's own input
preparation and config installation, the scenario's execution (start
SPADE, call the scenario's `scenario_execute`, stop SPADE), the scenario's
own output report, and finally shuts the broker down. `main` itself calls
only its own small, locally-defined wrapper functions between banners --
never a `common/` or `scenario.sh` function directly -- so the top-level
shape of a provisioning run is visible in one place without chasing
implementation details.

Several `common/*.sh` functions call `exit` directly on failure (a control
command SPADE rejects, a broker or control port that never comes up, a
missing input file) -- since bash runs an `EXIT` trap no matter how or
from where the process exits, `cleanup` still fires and stops whichever of
SPADE/the broker was actually left running, even when a run dies partway
through. On the normal path, both are already stopped by the time
`cleanup` runs, so it's a no-op there.

## `convert-storage-output-to-json.sh`

Standalone -- not invoked by `provision.sh`. Takes one positional argument
(a storage file-writer's raw Avro binary output file, produced when a
scenario's installed `cfg/spade.storage.<Class>.config` has
`kafka.output.file` set) and converts it to JSON alongside the input file.
Uses `common/util.sh`'s jar-locating helper.

## `common/`

The shared, scenario-agnostic library both scripts above are built from --
SPADE and Kafka broker lifecycle, dataset downloading, and utility
helpers. See `common/README.md`.
