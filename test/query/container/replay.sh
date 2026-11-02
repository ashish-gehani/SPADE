#!/bin/bash
# Replay a recorded trace into fresh storages: the Audit reporter reads the input
# trace.sh saved (spade-input.log, everything the reporter read from the audit
# bridge) instead of auditd, with the trace's own arguments. The trace's ground
# truth is linked next to the result, so query.sh, query-trace3.sh and the
# checkers run on a replay as on the trace. Used to check reporter and storage
# fixes on a trace without recording it again; the containers' activity is the
# same, the reporter and storages are the build's.
# Usage: replay.sh N [NAME]   Input: ~/stage-b/trace<N>/   Output: ~/stage-b/trace<NAME>/ (<N>r by default)
# GIVE_HANDLE=0 leaves out the live trace's handleLocalEndpoints (BUG_FIXES.md N20, F16).
set -u
HERE=$(cd "$(dirname "$0")" && pwd)   # this directory: the other scripts
N=${1:?usage: replay.sh 1|2|3|4 [NAME]}
NAME=${2:-${N}r}
SRC="$HOME/stage-b/trace$N"
OUT="$HOME/stage-b/trace$NAME"
case "${N%%[!0-9]*}" in   # the trace number: 1c is a recording of trace 1
    1|3|4) ARGS="namespaces=true fileIO=true netIO=true IPC=true inode=true networkAddressTranslation=true" ;;
    2) ARGS="namespaces=true" ;;
    *) echo "usage: replay.sh 1|2|3|4 [NAME]"; exit 2 ;;
esac
[ -s "$SRC/spade-input.log" ] || { echo "ABORT: no $SRC/spade-input.log"; exit 1; }
[ "$OUT" != "$SRC" ] || { echo "ABORT: the replay would overwrite the trace"; exit 1; }
# In playback the reporter ignores the live-only keys (networkAddressTranslation)
# and handles the kernel module's records it finds, netfilter's included.
ARGS="inputLog=$SRC/spade-input.log $ARGS outputLog=$OUT/spade-input.log"
rm -rf "$OUT"; mkdir -p "$OUT"
for f in truth.txt sampler.jsonl auditd-slice.log; do
    [ -e "$SRC/$f" ] && ln -s "$SRC/$f" "$OUT/$f"
done
exec > >(tee "$OUT/run.log") 2>&1
# the SPADE tree these scripts are in, unless SPADE_HOME names another
SPADE_HOME=${SPADE_HOME:-$(cd "$HERE/../../.." && pwd)}
cd "$SPADE_HOME" || exit 1
# Whether to use the kernel module's network records, as the live trace did. Left
# unset, playback decides by the first network event in the log, and a network
# SYSCALL record first (trace 3 has some) makes it ignore every module network
# record, so the replay has no sockets (BUG_FIXES.md N20).
LIVE_LOG=$(sed -n 's/^SPADE log: //p' "$SRC/run.log" | head -1)
HANDLE=$(grep -oE 'handleLocalEndpoints=(true|false)' "$LIVE_LOG" 2>/dev/null | head -1)
[ -n "$HANDLE" ] || { echo "ABORT: no handleLocalEndpoints in the trace's SPADE log '$LIVE_LOG'"; exit 1; }
if [ "${GIVE_HANDLE:-1}" = 1 ]; then
    ARGS="$ARGS $HANDLE"
    echo "as the live trace ($LIVE_LOG): $HANDLE"
else
    # GIVE_HANDLE=0: leave it to playback to decide, the case of N20
    echo "handleLocalEndpoints not given (the live trace had $HANDLE)"
fi

ctl() {
    echo ">> control: $*"
    printf '%s\nexit\n' "$*" | timeout 300 bin/spade control 2>&1 \
        | sed -n '/^-> /,$p' | grep -vE '^-> exit|^\s*$'
}
mark() { echo "MARK $(date +%s.%3N) $*"; }

echo "replay of trace $N as trace$NAME  start=$(date -Is)  commit=$(git log --oneline -1)"
# The same fresh SPADE and storages as trace.sh.
bin/spade stop >/dev/null 2>&1
sleep 3
git checkout -- cfg/spade.client.Control.config
bin/spade start --mem-min 2g --mem-max 8g
sleep 10
SPADE_LOG=$(ls -t log/SPADE_*.log | head -1)
echo "SPADE log: $SPADE_LOG"
if ! printf 'list all\nexit\n' | timeout 60 bin/spade control 2>&1 | grep -q 'No storages added'; then
    echo "ABORT: SPADE started with storages already added"; exit 1
fi
ctl "add analyzer CommandLine"
ctl "add storage PostgreSQL reset=true"
ctl "add storage Neo4j reset=true"
QS_DB="$HOME/stage-b/quickstep-db"
bin/manage-quickstep.sh stop >/dev/null 2>&1
rm -rf "$QS_DB"
bin/manage-quickstep.sh start --path "$QS_DB" --memory 2048 >/dev/null 2>&1
for i in $(seq 60); do ss -ltn | grep -q ':3000 ' && break; sleep 1; done
ss -ltn | grep -q ':3000 ' || { echo "ABORT: the Quickstep server is not listening on port 3000"; exit 1; }
ctl "add storage Quickstep reset=true batchTimeIntervalInSeconds=30"

mark "reporter add"
ctl "add reporter Audit $ARGS"
# The reporter's event loop ends at the end of its input, once every event is handled.
for i in $(seq 21600); do grep -q 'Exiting event reader thread for SPADE audit bridge' "$SPADE_LOG" && break; sleep 1; done
mark "input handled"
echo "waiting 70 s so Neo4j commits its last transaction and Quickstep sends its last batch"
sleep 70
ctl "remove reporter Audit"
mark "done"

problems=""
n=$(grep -c 'Failed to persist' "$SPADE_LOG")
[ "$n" = 0 ] || problems="$problems storage-persist-failures:$n"
n=$(grep -cE 'Failed to execute a task' "$SPADE_LOG")
[ "$n" = 0 ] || problems="$problems neo4j-task-failures:$n"
n=$(grep -E 'spade\.storage\.(Quickstep|quickstep\.)' "$SPADE_LOG" | grep -c 'SEVERE')
[ "$n" = 0 ] || problems="$problems quickstep-errors:$n"
n=$(grep -cE 'Failed to (handle|parse) event' "$SPADE_LOG")
[ "$n" = 0 ] || problems="$problems reporter-errors:$n"
# the replay read all of its input
python3 "$HERE"/compare-input.py "$SRC/spade-input.log" "$OUT/spade-input.log" || problems="$problems input-not-read"
n=$(PGPASSWORD=12345 psql -h localhost -U spade -d spadedb -tAc 'select count(*) from vertex')
echo "PostgreSQL vertices: $n"
[ "${n:-0}" -gt 1000 ] || problems="$problems storage-nearly-empty:$n"
# every storage holds the same graph (a server that stopped answering shows only here)
counts=""
for s in PostgreSQL Neo4j Quickstep; do
    c=$(printf 'set storage %s\nstat $base\nexit\n' "$s" | timeout 300 bin/spade query 2>&1 \
        | awk -F'|' '/^\| [0-9]+ +\| [0-9]+/{gsub(/ /, "", $2); gsub(/ /, "", $3); print $2 " vertices, " $3 " edges"; exit}')
    echo "$s: ${c:-no answer in 300 s}"
    counts="$counts ${c:-none};"
done
git checkout -q -- cfg/spade.client.CommandLine.config   # the client saved its last storage
first=$(echo "$counts" | cut -d';' -f1)
[ "$counts" = "$first;$first;$first;" ] && ! echo "$counts" | grep -q none \
    || problems="$problems storages-differ"
grep -E 'WARNING' "$SPADE_LOG" | grep -E 'spade\.reporter' | sed -E 's/^.*(WARNING: )/\1/' | sort | uniq -c | head -5
if [ -z "$problems" ]; then
    echo "REPLAY: ok"
else
    echo "REPLAY: FAILED:$problems"
fi
