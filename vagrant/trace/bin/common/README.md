# bin/common/

Shared, scenario-agnostic library sourced by `provision.sh`. Nothing here
knows which scenario is selected -- every function takes whatever it needs
(class names, topics, marker strings, paths) as an argument.

Each file opens with a block of plain, non-`ENV_`-prefixed local variable
aliases for the `env/` constants it actually uses (e.g. `SPADE_HOME`
aliasing `ENV_SPADE_HOME`), and the functions below read using those short
names. Follow the same pattern in any new file added here.

## `helper.sh`

`helper_print_banner` -- prints a section banner. Used throughout
`provision.sh` to mark each stage of a run.

## `spade.sh`

SPADE checkout/build (`spade_setup`, which always rebuilds -- there is no
"already built" check), process lifecycle (`spade_start`, `spade_is_up`,
`spade_wait_for_control_port`, `spade_stop`), and control-client handling.

`spade_is_up` is a one-shot check (a single control-client connection
attempt, no retry loop) -- `spade_wait_for_control_port` polls it
repeatedly until it succeeds or attempts run out, and `provision.sh`'s
`cleanup` (its `EXIT` trap) calls it directly to decide whether SPADE
needs stopping on the way out.

`spade_start` always truncates the control-client config SPADE auto-saves
its running reporters/storages into on every stop (and auto-replays on
every start) before starting, via `spade_clear_control_client_config` --
otherwise whatever a previous scenario had running would silently
reappear.

`spade_stop` sends `bin/spade stop` (SIGTERM -- SPADE exits once its own
buffers clear, which can take an arbitrarily long time) and polls
`spade_is_stopped` (`bin/spade status`, not the control port -- the
process can still be mid-shutdown after the port's already gone quiet).
If it hasn't exited within `ENV_SPADE_STOP_WAIT_ATTEMPTS` x
`ENV_SPADE_STOP_WAIT_SLEEP_SECONDS`, falls back to `bin/spade kill`
(SIGKILL) so a run never leaves the process behind.

`spade_run_control_command` runs exactly one control command (never a
batch) and checks SPADE's own response text for success rather than
trusting the control client's exit code. Every add/remove handler in
SPADE's `Kernel.java` prints a progress prefix followed by `"... done"` on
success, and something else entirely on failure (`"error: ..."`, a bare
`"failed"`, or `"<Class> not found"` for a remove against something not
attached) -- so `spade_run_control_command` greps the response for the
`"... done"` substring, echoes the full output either way, and exits
non-zero on anything else. Scenario scripts call this directly, one
command at a time, rather than any generic driver code assembling or
batching commands on their behalf.

`spade_wait_for_log_marker` polls `log/current.log` for a fixed marker
string (e.g. the line a reporter logs right before its read loop exits),
used instead of a fixed sleep since how long a reporter takes depends on
its input size. It takes the marker string and a total timeout in seconds
-- attempts are derived from that timeout divided by the shared poll
interval (`ENV_SPADE_DONE_MARKER_WAIT_SLEEP_SECONDS`), rounded up.
Scenario scripts call this directly, passing their own marker string and
their own timeout (since how long is reasonable to wait depends on that
scenario's own input size). Unlike `spade_run_control_command`, a timeout
here only prints a warning and returns rather than exiting -- the reporter
may just be running slower than expected, and the scenario's own
remove/stop cleanup still needs to run either way.

## `kafka.sh`

A bare-minimum, single-node, plaintext Kafka broker in KRaft mode (no
authentication or encryption): `kafka_broker_setup`/`start`/`consume`/
`shutdown`/`uninstall`. Generic -- the topic to read is passed as an
argument to `kafka_broker_consume`, nothing here assumes which storage or
scenario is active. The KRaft log directory is wiped and reformatted on
every `kafka_broker_configure` (part of `setup`), so no prior run's topic
data can resurface.

## `datasets.sh`

`datasets_google_drive_download` -- a generic Google Drive downloader.
Tries a direct download endpoint first; if the response looks like an HTML
page instead of the file itself, falls back to the classic cookie +
confirm-token dance. Fails loudly rather than ever writing an HTML error
page to disk and treating it as data.

`datasets_prepare_tc_trace` -- downloads (once) and decompresses (once)
the shared DARPA TC trace using the function above, skipping either step
if its output already exists. Used by the two `cdm-to-*` scenarios' own
`scenario_prepare_input` hooks.

## `util.sh`

`util_require_avro_tools_jar` -- locates the `avro-tools` jar SPADE's own
build already pulls in as a Maven dependency, failing with build-step
guidance if it isn't there yet. Used by `convert-storage-output-to-json.sh`.
