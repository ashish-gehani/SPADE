#!/bin/bash
# F23: a QuickGrail query on Quickstep sees what the storage has received but not yet sent.
# Replays a recorded trace's input into Quickstep alone, at its default batch interval
# (180 s), and asks for the counts as soon as the reporter has read all of it, well before
# the storage's timer would send the last batch. Compare them with the trace's replay on all
# three storages (replay.sh). Usage: f23check.sh N   Input: ~/stage-b/trace<N>/spade-input.log
set -u
N=${1:?usage: f23check.sh N}
SRC="$HOME/stage-b/trace$N"
cd "${SPADE_HOME:-$HOME/spade-clarity}" || exit 1
[ -s "$SRC/spade-input.log" ] || { echo "ABORT: no $SRC/spade-input.log"; exit 1; }
ctl() { printf '%s\nexit\n' "$*" | timeout 300 bin/spade control 2>&1 | sed -n '/^-> /,$p' | grep -vE '^-> exit|^\s*$'; }

bin/spade stop >/dev/null 2>&1; sleep 3
git checkout -q -- cfg/spade.client.Control.config
QS_DB="$HOME/stage-b/quickstep-db"
bin/manage-quickstep.sh stop >/dev/null 2>&1
rm -rf "$QS_DB"
bin/manage-quickstep.sh start --path "$QS_DB" --memory 2048 >/dev/null 2>&1
for i in $(seq 60); do ss -ltn | grep -q ':3000 ' && break; sleep 1; done
bin/spade start --mem-min 2g --mem-max 8g >/dev/null 2>&1
sleep 10
SPADE_LOG=$(ls -t log/SPADE_*.log | head -1)
ctl "add analyzer CommandLine"
ctl "add storage Quickstep reset=true"   # the default batchTimeIntervalInSeconds, 180
t0=$(date +%s)
HANDLE=$(grep -oE 'handleLocalEndpoints=(true|false)' "$(sed -n 's/^SPADE log: //p' "$SRC/run.log" | head -1)" 2>/dev/null | head -1)
ctl "add reporter Audit inputLog=$SRC/spade-input.log namespaces=true $HANDLE"
for i in $(seq 3600); do grep -q 'Exiting event reader thread for SPADE audit bridge' "$SPADE_LOG" && break; sleep 1; done
t1=$(date +%s)
c=$(printf 'set storage Quickstep\nstat $base\nexit\n' | timeout 600 bin/spade query 2>&1 \
    | awk -F'|' '/^\| [0-9]+ +\| [0-9]+/{gsub(/ /, "", $2); gsub(/ /, "", $3); print $2 " vertices, " $3 " edges"; exit}')
t2=$(date +%s)
git checkout -q -- cfg/spade.client.CommandLine.config
echo "input read $((t1 - t0)) s after the reporter was added; Quickstep then: ${c:-no answer} (the query took $((t2 - t1)) s)"
grep -cE 'Quickstep.*SEVERE|SEVERE.*Quickstep' "$SPADE_LOG" | sed 's/^/Quickstep SEVERE messages: /'
ctl "remove reporter Audit" >/dev/null
bin/spade stop >/dev/null 2>&1
git checkout -q -- cfg/spade.client.Control.config
