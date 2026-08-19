# scenarios/

One subdirectory per scenario -- a specific, meaningful `(reporter,
storage)` pairing -- plus `scenario.sh`, a reference/template for the
contract every scenario implements.

| Scenario | Reporter | Storage | Data source |
|---|---|---|---|
| `cdm-to-json` | `spade.reporter.CDM` | `spade.storage.JSON` | downloaded TC trace |
| `cdm-to-kafka` | `spade.reporter.CDM` | `spade.storage.Kafka` | downloaded TC trace |
| `audit-to-cdm` | `spade.reporter.Audit` (FILE mode) | `spade.storage.CDM` | user-supplied audit log |
| `audit-to-kafka` | `spade.reporter.Audit` (FILE mode) | `spade.storage.Kafka` | user-supplied audit log |

Each scenario's `cfg/` directory holds its own, complete, non-shared
copies of whatever SPADE config files it needs -- never symlinked or
templated across scenarios, even where two scenarios' files start out
identical (`cdm-to-kafka` and `audit-to-kafka` both need a
`spade.storage.Kafka.config`, but as two separate files). This trades a
small amount of duplication for the property that hand-editing one
scenario's config can never accidentally change another's behavior. Every
scenario also has its own topic name, producer id, and file-writer output
path baked directly into its own config, so scenarios never collide even
when they share a storage class.

## Scenario file contract

Every `scenarios/<name>/scenario.sh` defines the same five functions and
one variable. `bin/provision.sh` is written once against this contract
and never branches on scenario name directly.

- `SCENARIO_DONE_MARKER_TIMEOUT_SECONDS` -- how long (in seconds)
  `scenario_execute`'s `spade_wait_for_log_marker` call should wait before
  giving up. Set per scenario rather than shared, since how long is
  reasonable depends on that scenario's own input size (the `cdm-to-*`
  scenarios stream a ~1.3 GiB trace and need real headroom; the
  `audit-to-*` scenarios' user-supplied logs are expected to be much
  smaller).
- `scenario_description` -- echoes one line, printed in banners.
- `scenario_prepare_input` -- prepares this scenario's input (download,
  decompress, or just verify a file exists) before SPADE is started. The
  `audit-to-*` scenarios also create the audit log's directory here (even
  though the log file itself isn't downloaded automatically), so there's
  always somewhere to drop one.
- `scenario_install_config` -- copies this scenario's own `cfg/` files
  into SPADE's `cfg/` directory, then creates the file writer's output
  directory (the parent of the installed storage config's
  `kafka.output.file`, if that line is active) so provisioning doesn't
  fail with a missing-directory error the moment someone uncomments it --
  SPADE's own `FileWriter` doesn't create it.
- `scenario_execute` -- runs while SPADE is up and the control port is
  ready. Owns its full sequence directly: `bin/common/spade.sh`'s
  `spade_run_control_command`, once per command (add storage, add
  reporter, later remove reporter, remove storage -- never batched, so
  each command's success/failure is checked before the next one runs),
  and `spade_wait_for_log_marker` with its own marker string and
  `SCENARIO_DONE_MARKER_TIMEOUT_SECONDS` in between to confirm the
  reporter actually finished -- a timeout there only warns rather than
  aborting, so the remove/stop calls after it still run.
  `bin/provision.sh` doesn't inspect or drive any of this, it just calls
  the one hook.
- `scenario_post_execution_work` -- reports this scenario's output after
  it's stopped: greps its own installed storage config for which
  writer(s) were active, consumes the topic if the server writer was, and
  prints the output file path if the file writer was.

`scenario.sh` in this directory implements every function above with a
documented, no-op body -- it is not sourced by `bin/provision.sh`, it
exists purely as the template to copy.

The class name(s) and config filename(s) each hook needs are hardcoded
directly in that scenario's own file rather than factored into shared
shell constants -- matching the same duplication-over-sharing choice
already made for `cfg/`.

## Adding a new scenario

1. Pick a name, ideally describing the `(reporter, storage)` pairing the
   same way the existing four do.
2. Create `scenarios/<name>/` and copy `scenario.sh` from this directory
   into it.
3. Set `SCENARIO_DONE_MARKER_TIMEOUT_SECONDS` and fill in each of the five
   functions -- use one of the four existing scenarios as a worked example
   of the pattern, especially `scenario_execute`'s one-command-at-a-time
   control calls.
4. Add `scenarios/<name>/cfg/` with the scenario's own storage config
   (and reporter config, if it needs one distinct from SPADE's shipped
   default) -- a fresh copy, never shared with another scenario's `cfg/`.
5. Add the new name to `ENV_SCENARIO_AVAILABLE` in `env/scenario.sh`. If
   the scenario needs a new kind of external input, add constants for it
   in `env/datasets.sh` alongside the existing entries.
6. Make the new `scenario.sh` executable and run `bash -n` over it before
   trying a real provisioning run.
7. Point `ENV_SCENARIO_SELECTED` at the new name and run
   `bin/provision.sh` (or `vagrant up`) to exercise it.
