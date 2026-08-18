# SPADE + DARPA TC CDM / Audit / Kafka

This Vagrant environment spins up a single VM that demonstrates SPADE's CDM
storage and Audit reporter end to end (and, for comparison, the generic
Kafka storage). It builds SPADE from source, stands up a local Kafka
broker, then replays real provenance data through one of four scenarios.

It supersedes `vagrant/kafka` and does not depend on it in any way.

## Scenarios

| Scenario name | Reporter | Storage | Data source |
|---|---|---|---|
| `cdm-to-cdm` (default) | `spade.reporter.CDM` | `spade.storage.CDM` | downloaded TC trace, works out of the box |
| `cdm-to-kafka` | `spade.reporter.CDM` | `spade.storage.Kafka` | downloaded TC trace, works out of the box |
| `audit-to-cdm` | `spade.reporter.Audit` (FILE mode) | `spade.storage.CDM` | user-supplied audit log |
| `audit-to-kafka` | `spade.reporter.Audit` (FILE mode) | `spade.storage.Kafka` | user-supplied audit log |

Pick a scenario by editing `ENV_SCENARIO_SELECTED` in `env/scenario.sh`
(one line) before running `vagrant up`, or before re-running
`bin/provision.sh` to switch scenarios without a full re-provision.

The `cdm-to-*` scenarios replay a downloaded DARPA Transparent Computing
(TC) Engagement 5 trace and work with no extra setup. The `audit-to-*`
scenarios expect a real auditd log at the path named by
`ENV_DATASETS_AUDIT_LOG_FILE` in `env/datasets.sh` -- nothing downloads
this automatically; place a log there (or repoint the constant) before
selecting one of these scenarios.

## What happens during provisioning

On `vagrant up`, the VM (Ubuntu 24.04) runs `bin/provision.sh`, which:

1. Builds SPADE from source (installs required packages, clones the
   `pidsmaker-dev` branch).
2. Sets up and starts a local, plaintext, single-node Kafka broker.
3. Prepares the selected scenario's input (downloads + decompresses the
   shared TC trace for `cdm-to-*`, or checks for the audit log for
   `audit-to-*`).
4. Installs that scenario's own config files into SPADE's `cfg/`.
5. Starts SPADE, adds the scenario's reporter and storage, waits for the
   reporter to finish, then removes them and stops SPADE.
6. Reports the scenario's output -- consumes its Kafka topic if the server
   writer was active, and/or prints the output file path if the file
   writer was active.
7. Shuts down the Kafka broker.

Every scenario is fully isolated: its own topic, producer id, and file
writer output path, all baked into its own non-shared `cfg/` files. SPADE
is fully stopped and freshly started on every run, and its auto-saved
control-client config is cleared before every start, so switching
scenarios never requires manual cleanup.

## Storage writer mode

Each scenario's own `cfg/spade.storage.<Class>.config` controls whether
its storage writes to the Kafka broker, to a local file, or both:

- `kafka.output.server`/`kafka.output.topic`/`kafka.output.producer.id` set
  (the default) -- publishes to the local broker.
- `kafka.output.file=<path>` uncommented -- writes straight to a local
  file, no broker involved. This is raw Avro binary (self-describing,
  schema embedded), not human-readable JSON.
- Both can be active at once.

## Converting file-writer output to JSON

If a scenario's file writer was active, `bin/provision.sh` prints the
output file's path. Convert it to JSON afterwards with:

```
bin/convert-storage-output-to-json.sh <output>.bin
```

This is a standalone script, not run automatically during provisioning. It
uses the `avro-tools` jar SPADE's own build already pulls in as a Maven
dependency, and writes `<output>.json` alongside the input file.

## Directory layout

- `bin/` -- the driver (`provision.sh`), the standalone JSON-conversion
  script, and the shared library they're built from. See
  `bin/README.md`.
- `env/` -- every constant this VM uses, one file per category. See
  `env/README.md`.
- `scenarios/` -- the four scenarios above, the contract every scenario
  implements, and how to add a new one. See `scenarios/README.md`.

## Usage

```
vagrant up
```

Provisioning output will show each stage running, ending with the
scenario's output (consumed topic contents and/or output file path).
