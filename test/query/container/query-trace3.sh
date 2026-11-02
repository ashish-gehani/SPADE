#!/bin/bash
# Trace 3's queries as run for TRACE_VALIDATION.md, after `query.sh 3` on PostgreSQL.
#
# truth.txt names only the namespaces Docker could be asked about; a run that
# crashed at once (the stack restarts its services until the names they need
# resolve) was gone by then. So the namespaces come from the kernel module's own
# records of the container inits (ns pid 1) in auditd's log: every namespace not
# queried yet, and every ID used by more than one run in all its forms.
#
# PostgreSQL gets all of them. Neo4j and Quickstep get a subset, since a Neo4j
# boundary takes about 230 s against 20 s on PostgreSQL: getContainerInit(), the
# boundary of everything, the first five IDs of truth.txt, the reused IDs, one
# first run, and the namespace of the run whose clone record auditd dropped.
# (On Neo4j, the first seven came from `query.sh 3` itself, stopped after them.)
# TRACE names another trace directory, e.g. TRACE=3r for replay.sh's replay of trace 3.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
TRACE=${TRACE:-3}
T="$HOME/stage-b/trace$TRACE"

inits=$(grep -a 'ns_operation=ns_NEWPROCESS' "$T/auditd-slice.log" | grep -a ' ns_ns_pid=1 ' \
    | grep -av 'ns_inum_pid=4026531836 ' | grep -oE 'ns_inum_pid=[0-9]+' | cut -d= -f2)
queried=$(grep -oE '\b[XY]=[0-9]+' "$T/truth.txt" | cut -d= -f2 | sort -u)
reused=()
for x in $(echo "$inits" | sort | uniq -c | awk '$1 > 1 {print $2}'); do
    reused+=("getContainerBoundary('$x')")
    for n in $(seq "$(echo "$inits" | grep -cx "$x")"); do reused+=("getContainerBoundary('$x', $n)"); done
done
first=()
for x in $(echo "$inits" | sort | uniq -c | awk '$1 == 1 {print $2}'); do
    echo "$queried" | grep -qx "$x" || first+=("getContainerBoundary('$x')")
done
echo "reused: ${#reused[@]} calls, first runs: ${#first[@]} calls"

STORAGES=PostgreSQL ONLY_EXTRA=1 bash "$HERE/query.sh" "$TRACE" "${reused[@]}" "${first[@]}"

# the run with the dropped clone record (TRACE_VALIDATION.md, trace 3)
dropped=4026533315
subset=("${reused[@]}" "${first[0]}" "getContainerBoundary('$dropped')")
STORAGES=Neo4j ONLY_EXTRA=1 bash "$HERE/query.sh" "$TRACE" "${subset[@]}"
five=$(echo "$queried" | head -5 | sed "s/.*/getContainerBoundary('&')/")
mapfile -t five <<< "$five"
STORAGES=Quickstep ONLY_EXTRA=1 bash "$HERE/query.sh" "$TRACE" "getContainerInit()" "getContainerBoundary()" \
    "${five[@]}" "${subset[@]}"
