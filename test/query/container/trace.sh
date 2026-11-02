#!/bin/bash
# Stage B: record one real trace with SPADE, PostgreSQL, Neo4j and Quickstep attached.
# Scenarios and expectations are in TRACE_VALIDATION.md, Part B (committed first).
# Run as vagrant, SPADE's user: SPADE leaves that user's activity out, so every
# scenario runs under sudo or inside containers.
# Usage: trace.sh 1|2|3|4 [NAME]    Output: ~/stage-b/trace<NAME>/ (run.log, truth.txt), trace<N> by default
#   1: B1-B7   2: B8-B11, netIO off   3: M, the DeathStarBench media microservices
#   4: S, six of the media microservices' containers and a small workload
set -u
HERE=$(cd "$(dirname "$0")" && pwd)   # this directory: the other scripts
N=${1:-}
NAME=${2:-$N}   # e.g. 1b, to keep an earlier recording of trace 1
case "$N" in
    1|3|4) ARGS="namespaces=true fileIO=true netIO=true IPC=true inode=true networkAddressTranslation=true" ;;
    2) ARGS="namespaces=true" ;;
    *) echo "usage: trace.sh 1|2|3|4"; exit 2 ;;
esac
MEDIA="$HOME/DeathStarBench/mediaMicroservices"
# Trace 4 starts only these, without what they depend on otherwise
SUBSET="dns-media jaeger nginx-web-server user-service user-mongodb user-memcached"
UP_ARGS=""; COMPOSE_SERVICES=""
[ "$N" = 4 ] && UP_ARGS="--no-deps $SUBSET" && COMPOSE_SERVICES="$SUBSET"
# Leave out infrastructure that floods the audit stream but has nothing to do
# with containers: udev answers every veth a container gets with thousands of
# opens and closes (its workers are children of systemd-udevd), and postgres is
# SPADE's own storage. The first three are the reporter's defaults, which these
# arguments replace. Recording note: this differs from the namespace-aware
# config, which keeps only the defaults.
IGNORE="kauditd,auditd,audispd,systemd-udevd,postgres"
ARGS="$ARGS ignoreProcesses=$IGNORE ignoreParentProcesses=$IGNORE"
# Everything the reporter reads from the audit bridge, to compare with auditd's
# own log (auditd-slice.log) when the graph misses something auditd recorded.
ARGS="$ARGS outputLog=$HOME/stage-b/trace$NAME/spade-input.log"
# The media stack produces audit events about five times faster than SPADE
# stores them with three storages, so the bridge must hold a large backlog.
[ "$N" = 3 ] && ARGS="$ARGS spadeAuditBridgeQueueMegabytes=4096"
OUT="$HOME/stage-b/trace$NAME"
rm -rf "$OUT"; mkdir -p "$OUT"
exec > >(tee "$OUT/run.log") 2>&1
# the SPADE tree these scripts are in, unless SPADE_HOME names another
SPADE_HOME=${SPADE_HOME:-$(cd "$HERE/../../.." && pwd)}
cd "$SPADE_HOME" || exit 1
TRUTH="$OUT/truth.txt"   # what the kernel says: each scenario's PID namespace IDs

# Run one controller command; print only what follows the startup banner.
ctl() {
    echo ">> control: $*"
    printf '%s\nexit\n' "$*" | timeout 300 bin/spade control 2>&1 \
        | sed -n '/^-> /,$p' | grep -vE '^-> exit|^\s*$'
}
mark() { echo "MARK $(date +%s.%3N) $*"; }
pidns_of() { sudo readlink "/proc/$1/ns/pid" | tr -dc '0-9'; }
truth() { echo "$*" | tee -a "$TRUTH"; }
digits() { tr -dc '0-9\n' | grep -m1 . ; }

echo "trace $N  start=$(date -Is)  commit=$(git log --oneline -1)"
# F7 (BUG_FIXES.md) prints an unreadable namespace as -1; only that build has the format string.
echo "kernel module has F7: $(strings lib/kernel-modules/spade_audit.ko | grep -q '%s=-1' && echo yes || echo no)"
# A fresh SPADE for every trace, with a heap that fits the VM: the server
# defaults (-Xms8G -Xmx16G) starved an 8 GB guest. Query a trace before
# recording the next one: the storages are reset when they are added.
bin/spade stop >/dev/null 2>&1
sleep 3
# SPADE saves its running configuration on shutdown and replays it on start,
# reset=true included. Start from the tracked file, which adds nothing.
git checkout -- cfg/spade.client.Control.config
bin/spade start --mem-min 2g --mem-max 8g
sleep 10
bin/spade status
echo "memory: $(free -g | awk 'NR==2{print $2 " GB total, " $7 " GB available"}')"
SPADE_LOG=$(ls -t log/SPADE_*.log | head -1)
echo "SPADE log: $SPADE_LOG"
if ! printf 'list all\nexit\n' | timeout 60 bin/spade control 2>&1 | grep -q 'No storages added'; then
    echo "ABORT: SPADE started with storages already added"; exit 1
fi
ctl "add analyzer CommandLine"
ctl "add storage PostgreSQL reset=true"
ctl "add storage Neo4j reset=true"
# Quickstep, the third storage QuickGrail can query: a fresh server and database
# for every trace. Its storage sends a batch only when the batch is full or its
# timer fires (180 s by default), and queries do not flush it, so use 30 s, the
# lowest it accepts (it raises lower values to 30 with a warning).
QS_DB="$HOME/stage-b/quickstep-db"
bin/manage-quickstep.sh stop >/dev/null 2>&1
rm -rf "$QS_DB"
bin/manage-quickstep.sh start --path "$QS_DB" --memory 2048 >/dev/null 2>&1
for i in $(seq 60); do ss -ltn | grep -q ':3000 ' && break; sleep 1; done
ss -ltn | grep -q ':3000 ' || { echo "ABORT: the Quickstep server is not listening on port 3000"; exit 1; }
echo "Quickstep server: $(bin/manage-quickstep.sh status), database $QS_DB"
ctl "add storage Quickstep reset=true batchTimeIntervalInSeconds=30"

sudo docker rm -f b2 b3 b4 >/dev/null 2>&1
if [ "$N" = 1 ]; then
    # B3: a container that exists before tracing starts.
    sudo docker run -d --name b3 alpine sh -c 'while true; do sleep 2; done' >/dev/null
    sleep 2
    p=$(sudo docker inspect -f '{{.State.Pid}}' b3)
    truth "B3 before-tracing init_host_pid=$p X=$(pidns_of "$p")"
elif [ "$N" = 2 ]; then
    # B10's program, built before tracing so the compiler is not in the trace
    gcc -static -O2 -o "$OUT/spawn-child" "$HERE"/spawn-child.c \
        || { echo "ABORT: could not build spawn-child (static glibc)"; exit 1; }
else
    # M: the stack's containers exist and are stopped when tracing starts
    (cd "$MEDIA" && sudo docker compose stop) > "$OUT/compose-stop-before.log" 2>&1
    echo "media containers: $(cd "$MEDIA" && sudo docker compose ps -a -q | wc -l), running: $(cd "$MEDIA" && sudo docker compose ps -q | wc -l)"
    # The stack's own restart policy, restart: always: services that start before
    # the names they need resolve crash and are restarted. Set back to no after.
    sudo docker update --restart always $(cd "$MEDIA" && sudo docker compose ps -a -q) >/dev/null
    # the sampler runs as this user, which the audit rules leave out
    python3 "$HERE"/cgroup-sampler.py "$OUT/sampler.jsonl" 0.1 2> "$OUT/sampler.err" &
    SAMPLER=$!
fi

# make rebuilds the bridge without the setuid bit allowAuditAccess gave it, and
# the bridge then can't read auditd's socket; SPADE logs that only as INFO.
[ -u bin/spadeAuditBridge ] || { echo "ABORT: bin/spadeAuditBridge is not setuid; run bin/allowAuditAccess"; exit 1; }
lost() { sudo auditctl -s | awk '$1 == "lost" {print $2}'; }
if [ "$N" -ge 3 ]; then
    # auditd reads the kernel's records from a netlink socket whose receive buffer
    # is net.core.rmem_default when auditd starts (212992 bytes; auditd has no
    # setting for it). When the buffer stays full for 0.1 s, kauditd gives up on
    # the records it holds, and unless the kernel was booted with audit=1 it drops
    # them without counting them as lost (Linux 6.8 kernel/audit.c,
    # kauditd_hold_skb). The media stack's bursts lost 289k and 355k events this
    # way. auditd is restarted with a 512 MB buffer for the trace's bursts;
    # rmem_default is restored at once.
    # What made auditd too slow is its log_format, ENRICHED (Ubuntu's default),
    # which adds user and group names to each record. The services run as uids
    # the host does not know (memcached's 11211), and auditd looks each of them up
    # again for every record, through /etc/nsswitch.conf (files, systemd, sss),
    # without keeping the failure: per 2,000 such events, 12,858 opens and 839
    # socket connections, none for a known uid or with log_format RAW. systemd
    # answers the queries, which the trace records too (8% of its events). For
    # the trace, log_format is RAW: the records without the names, which SPADE
    # does not read. restore_auditd puts ENRICHED and the default buffer back,
    # also if the script stops early.
    # The restart reloads the audit rules, which resets the backlog limit, so all
    # this comes first.
    restore_auditd() {
        sudo grep -q '^log_format = RAW$' /etc/audit/auditd.conf || return 0
        sudo sed -i -E 's/^log_format = RAW$/log_format = ENRICHED/' /etc/audit/auditd.conf
        sudo systemctl restart auditd
        echo "auditd back to $(sudo grep '^log_format' /etc/audit/auditd.conf), its netlink receive buffer: $(sudo ss -f netlink -m -p | grep -A1 auditd | grep -oE 'rb[0-9]+' | head -1 | cut -c3-) bytes"
    }
    sudo grep -q '^log_format = ENRICHED$' /etc/audit/auditd.conf \
        || { echo "ABORT: expected log_format = ENRICHED in /etc/audit/auditd.conf"; exit 1; }
    sudo sed -i -E 's/^log_format = ENRICHED$/log_format = RAW/' /etc/audit/auditd.conf
    trap restore_auditd EXIT
    OLD_RMEM=$(sysctl -n net.core.rmem_default)
    sudo sysctl -q -w net.core.rmem_default=536870912
    sudo systemctl restart auditd
    sleep 2
    sudo sysctl -q -w net.core.rmem_default="$OLD_RMEM"
    echo "auditd restarted (pid $(pidof auditd)) with $(sudo grep '^log_format' /etc/audit/auditd.conf), its netlink receive buffer: $(sudo ss -f netlink -m -p | grep -A1 auditd | grep -oE 'rb[0-9]+' | head -1 | cut -c3-) bytes; rmem_default back to $(sysctl -n net.core.rmem_default)"
fi
echo "audit before:$(sudo auditctl -s | grep -E '^(lost|backlog_limit|backlog_wait_time) ' | tr '\n' ' ')"
# Ubuntu's default backlog limit of 8192 overflowed during container starts.
# Part A ran with 65536 and lost nothing. Restored at the end.
OLD_LIMIT=$(sudo auditctl -s | awk '$1 == "backlog_limit" {print $2}')
# Trace 3 overflowed even 65536 while auditd lagged behind the media stack.
LIMIT=65536; [ "$N" -ge 3 ] && LIMIT=262144
sudo auditctl -b "$LIMIT" >/dev/null
echo "backlog limit raised: $OLD_LIMIT -> $(sudo auditctl -s | awk '$1 == "backlog_limit" {print $2}')"
# auditd's af_unix plugin drops an event when its socket to the bridge is full,
# and that socket's send buffer is net.core.wmem_default when the bridge
# connects (212992 bytes here). Even with the bridge reading in a thread of its
# own, bursts lost a few hundred events. Raised for the trace, restored after.
OLD_WMEM=$(sysctl -n net.core.wmem_default)
# 16 MB holds about 1.6 s of the media stack's events; trace 3 gets 128 MB.
WMEM=16777216; [ "$N" -ge 3 ] && WMEM=134217728
sudo sysctl -q -w net.core.wmem_default="$WMEM"
echo "socket send buffer default raised: $OLD_WMEM -> $(sysctl -n net.core.wmem_default)"
LOST_BEFORE=$(lost)
SINCE=$(date '+%F %T')
if [ "$N" -ge 3 ]; then
    # Once a second: CPU ticks of auditd, kauditd, the bridge and SPADE, the state
    # of auditd's main thread (R running, S sleeping, D blocked on I/O),
    # the memory queued in auditd's netlink socket and its drop count, and the
    # system's dirty page cache in kB. It reads /proc only, as this user, so it
    # adds nothing to the trace.
    ticks() { [ -n "$1" ] && awk '{print $14 + $15}' /proc/"$1"/stat 2>/dev/null || echo -; }
    (
        A=$(pidof auditd); K=$(pgrep -x kauditd)
        while :; do
            echo "$(date +%s) auditd=$(ticks "$A") state=$(awk '{print $3}' /proc/"$A"/task/"$A"/stat 2>/dev/null) kauditd=$(ticks "$K") bridge=$(ticks "$(pgrep -x spadeAuditBridg)") spade=$(ticks "$(pgrep -o -f spade.core.Kernel)") $(awk -v p="$A" '$2 == 9 && $3 == p {print "rmem=" $5 " drops=" $9}' /proc/net/netlink) dirty=$(awk '$1 == "Dirty:" {print $2}' /proc/meminfo)"
            sleep 1
        done
    ) > "$OUT/monitor.log" 2>&1 &
    MONITOR=$!
fi
mark "reporter add"
ctl "add reporter Audit $ARGS"
sleep 10
echo "modules loaded: $(lsmod | awk '/^spade/{print $1}' | tr '\n' ' ')"
# auditd's af_unix plugin takes one client; a second one is closed at once.
BRIDGE_FAILURE='SPADE audit bridge.*(Unable to connect|Server closed the connection|Unable to start the socket reader)'
if grep -qE "$BRIDGE_FAILURE" "$SPADE_LOG"; then
    echo "ABORT: the audit bridge lost auditd's socket: $(grep -m1 -E "$BRIDGE_FAILURE" "$SPADE_LOG" | sed 's/.*OUTPUT\] //')"
    ctl "remove reporter Audit"; exit 1
fi
echo "module filter lists: $(sudo dmesg | grep 'spade_audit_controller.*arg_print' | tail -1 | grep -oE 'pids=\[[^]]*\]|ppids=\[[^]]*\]' | tr '\n' ' ')"
ctl "list all"

if [ "$N" = 1 ]; then
    mark "B1 begin"
    X=$(sudo docker run --rm alpine sh -c 'readlink /proc/self/ns/pid; sleep 1; true' | digits)
    truth "B1 docker-run X=$X"
    mark "B1 end"; sleep 1

    mark "B2 begin"
    sudo docker run -d --name b2 alpine sleep 60 >/dev/null
    sleep 2
    p=$(sudo docker inspect -f '{{.State.Pid}}' b2)
    truth "B2 docker-exec init_host_pid=$p X=$(pidns_of "$p")"
    sudo docker exec b2 sh -c 'sleep 1; true'
    mark "B2 end"; sleep 1

    mark "B4 begin"
    sudo docker run -d --name b4 --privileged alpine \
        unshare -p -f -m --mount-proc sh -c 'readlink /proc/self/ns/pid; sleep 3; true' >/dev/null
    sleep 1
    p=$(sudo docker inspect -f '{{.State.Pid}}' b4)
    X=$(pidns_of "$p")
    sudo docker wait b4 >/dev/null
    Y=$(sudo docker logs b4 2>&1 | digits)
    sudo docker rm b4 >/dev/null
    truth "B4 nested init_host_pid=$p X=$X Y=$Y"
    mark "B4 end"; sleep 1

    mark "B5 begin"
    # Through a root shell: SPADE's audit rules leave out uid 1000, the user it runs
    # as, by real uid, and sudo keeps that real uid, so a process sudo itself forks
    # is never recorded as created. The shell's children are.
    Y=$(sudo sh -c "unshare --pid --fork --mount-proc sh -c 'readlink /proc/self/ns/pid; sleep 1; true'" | digits)
    truth "B5 host-unshare Y=$Y"
    mark "B5 end"; sleep 1

    mark "B6 begin"
    for i in 1 2 3 4 5 6; do
        X=$(sudo docker run --rm alpine sh -c 'readlink /proc/self/ns/pid' | digits)
        truth "B6 reuse run=$i X=$X"
    done
    mark "B6 end"; sleep 1

    mark "B7 begin"
    X=$(sudo docker run --rm alpine sh -c 'readlink /proc/self/ns/pid; wget -q -T 5 -O /dev/null http://archive.ubuntu.com/; true' | digits)
    truth "B7 network-netIO-on X=$X"
    mark "B7 end"
    sleep 5   # B3's loop keeps forking sleeps
elif [ "$N" = 2 ]; then
    mark "B8 begin"
    Y=$(sudo sh -c "bwrap --unshare-pid --dev-bind / / sh -c 'readlink /proc/self/ns/pid; sleep 1; true'" | digits)   # root shell, as in B5
    truth "B8 bwrap Y=$Y"
    mark "B8 end"; sleep 1

    mark "B9 begin"
    X=$(sudo docker run --rm alpine sh -c 'readlink /proc/self/ns/pid; wget -q -T 5 -O /dev/null http://archive.ubuntu.com/; true' | digits)
    truth "B9 network-netIO-off X=$X"
    mark "B9 end"; sleep 1

    mark "B10 begin"
    out=$(sudo docker run --rm -v "$OUT/spawn-child:/spawn-child:ro" alpine \
        sh -c 'readlink /proc/self/ns/pid; /spawn-child')
    truth "B10 clone3-in-container X=$(echo "$out" | digits) child_ns_pid=$(echo "$out" | sed -n 2p)"
    mark "B10 end"; sleep 1

    mark "B11 begin"
    # directly under sudo, without the root shell of B5 and B8 (F18)
    Y=$(sudo unshare --pid --fork sh -c 'readlink /proc/self/ns/pid; sleep 1; true' | digits)
    truth "B11 sudo-unshare Y=$Y"
    mark "B11 end"
elif [ "$N" -ge 3 ]; then
    # Ground truth for every container start, restarts included: its init and PID
    # namespace, read as the start event arrives (a crashing run may be gone already).
    sudo docker events --filter type=container --filter event=start \
        --format '{{.Actor.ID}} {{.Actor.Attributes.name}}' 2>/dev/null | while read -r id name; do
        p=$(sudo docker inspect -f '{{.State.Pid}}' "$id" 2>/dev/null)
        x=unknown; [ -n "$p" ] && [ "$p" != 0 ] && x=$(pidns_of "$p")
        echo "M start container=$name id=$id time=$(date +%s.%3N) init_host_pid=${p:-0} X=${x:-unknown}" >> "$TRUTH"
    done &
    EVENTS=$!
    sleep 1
    mark "M compose-up begin"
    (cd "$MEDIA" && sudo docker compose up -d $UP_ARGS) > "$OUT/compose-up.log" 2>&1
    mark "M compose-up end"
    for i in $(seq 180); do
        [ "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/)" = 200 ] && break; sleep 1
    done
    echo "web server answers: $(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/) after ${i}s"
    mark "M register begin"
    if [ "$N" = 3 ]; then
        (cd "$MEDIA" && myenv/bin/python3 scripts/write_movie_info.py -c datasets/tmdb/casts.json \
            -m datasets/tmdb/movies.json --server_address http://127.0.0.1:8080 \
            && bash scripts/register_users.sh && bash scripts/register_movies.sh) > "$OUT/register.log" 2>&1
        echo "registration exit=$?"
    else
        ok=0
        for i in $(seq 100); do
            c=$(curl -s -o /dev/null -w '%{http_code}' -d "first_name=f$i&last_name=l$i&username=trace4_$(date +%s)_$i&password=p$i" \
                http://127.0.0.1:8080/wrk2-api/user/register)
            [ "$c" = 200 ] && ok=$((ok + 1))
        done
        echo "registration: $ok of 100 users"
    fi
    mark "M register end"
    if [ "$N" = 3 ]; then
        mark "M load begin"
        "$HOME/DeathStarBench/wrk2/wrk" -D exp -t 2 -c 4 -d 30 -L \
            -s "$MEDIA/wrk2/scripts/media-microservices/compose-review.lua" \
            http://127.0.0.1:8080/wrk2-api/review/compose -R 20 > "$OUT/wrk2.log" 2>&1
        echo "wrk2: $(grep -E 'Requests/sec|requests in' "$OUT/wrk2.log" | tr -s ' ' | tr '\n' ';')"
        mark "M load end"
    fi
    kill "$SAMPLER"; wait "$SAMPLER" 2>/dev/null
    cat "$OUT/sampler.err"
    # (not pkill -f: its own sudo would match the pattern)
    for p in $(ps -eo pid,args | awk '/[d]ocker events --filter/{print $1}'); do sudo kill "$p"; done
    kill "$EVENTS" 2>/dev/null
    # ground truth: each container's last run, and how often Docker restarted it
    for c in $(cd "$MEDIA" && sudo docker compose ps -a -q $COMPOSE_SERVICES); do
        p=$(sudo docker inspect -f '{{.State.Pid}}' "$c")
        n=$(sudo docker inspect -f '{{.Name}}' "$c" | tr -d /)
        x=exited; [ "$p" != 0 ] && x=$(pidns_of "$p")
        truth "M container=$n id=$(sudo docker inspect -f '{{.Id}}' "$c") restarts=$(sudo docker inspect -f '{{.RestartCount}}' "$c") init_host_pid=$p last_X=$x"
    done
    echo "media containers: $(grep -c '^M container=' "$TRUTH"), running: $(grep '^M container=' "$TRUTH" | grep -vc 'last_X=exited'), starts seen: $(grep -c '^M start ' "$TRUTH")"
fi

sleep 2
mark "reporter remove"
ctl "remove reporter Audit"
# The bridge hands SPADE everything it has queued before it exits, and only then
# is the reporter gone: with a large backlog, minutes after the control command
# above has timed out.
asked=$(date +%s)
for i in $(seq 7200); do ps -eo args | grep -q "[s]padeAuditBridge -s" || break; sleep 1; done
mark "audit bridge exited"
echo "audit bridge exited $(( $(date +%s) - asked )) s after the reporter was asked to stop"
[ -n "${MONITOR:-}" ] && kill "$MONITOR" 2>/dev/null
echo "audit after:  $(sudo auditctl -s | grep -E '^(lost|backlog_limit|backlog_wait_time) ' | tr '\n' ' ')"
LOST_AFTER=$(lost)
sudo auditctl -b "$OLD_LIMIT" >/dev/null
sudo sysctl -q -w net.core.wmem_default="$OLD_WMEM"
# auditd's own records for the trace, from every rotated file still on disk
T0=$(awk '/^MARK .* reporter add/{print $2}' "$OUT/run.log" | cut -d. -f1)
T1=$(date +%s)
sudo sh -c 'cat $(ls -tr /var/log/audit/audit.log*)' \
    | awk -F'[(:]' -v a="$T0" -v b="$T1" '/msg=audit\(/{t=$2+0; if (t>=a && t<=b) print}' > "$OUT/auditd-slice.log"
echo "auditd records in the trace window: $(wc -l < "$OUT/auditd-slice.log"), first at $(head -1 "$OUT/auditd-slice.log" | grep -oE 'audit\([0-9.]+' | cut -c7-)"
[ "$N" -ge 3 ] && restore_auditd
sudo docker rm -f b2 b3 >/dev/null 2>&1
if [ "$N" -ge 3 ]; then
    (cd "$MEDIA" && sudo docker compose stop) > "$OUT/compose-stop-after.log" 2>&1
    sudo docker update --restart no $(cd "$MEDIA" && sudo docker compose ps -a -q) >/dev/null
fi
echo "waiting 70 s so Neo4j commits its last transaction (PostgreSQL flushes before every query)"
sleep 70
mark "done"

# Completeness: a trace is judged only if no record was lost on the way to the
# storages. Otherwise it is re-recorded, not interpreted.
problems=""
[ "$LOST_AFTER" = "$LOST_BEFORE" ] || problems="$problems kernel-lost:$LOST_BEFORE->$LOST_AFTER"
n=$(sudo journalctl --no-pager -o cat --since "$SINCE" -u auditd | grep -ciE 'no buffer space|queue.*full|dropp')
[ "$n" = 0 ] || problems="$problems auditd-errors:$n"
n=$(grep -c 'Failed to persist' "$SPADE_LOG")
[ "$n" = 0 ] || problems="$problems storage-persist-failures:$n"
n=$(grep -cE 'Failed to execute a task' "$SPADE_LOG")
[ "$n" = 0 ] || problems="$problems neo4j-task-failures:$n"
n=$(grep -E 'spade\.storage\.(Quickstep|quickstep\.)' "$SPADE_LOG" | grep -c 'SEVERE')
[ "$n" = 0 ] || problems="$problems quickstep-errors:$n"
# Events lost between auditd and SPADE: the bridge's own count of missing serial
# numbers, and a direct comparison of auditd's log with what the reporter read.
n=$(grep -oE 'Audit events not received in total \(missing serial numbers\): [0-9]+' "$SPADE_LOG" | tail -1 | grep -oE '[0-9]+$')
echo "bridge: events not received: ${n:-no count reported (bridge without the count)}"
[ "${n:-0}" = 0 ] || problems="$problems bridge-missing-serials:$n"
grep -E 'Socket queue: peak' "$SPADE_LOG" | tail -1 | sed 's/.*OUTPUT\] /bridge: /'
python3 "$HERE"/compare-input.py "$OUT/auditd-slice.log" "$OUT/spade-input.log" || problems="$problems missing-from-spade-input"
# An empty trace loses nothing, so also require that the storage holds it.
n=$(PGPASSWORD=12345 psql -h localhost -U spade -d spadedb -tAc 'select count(*) from vertex')
echo "PostgreSQL vertices: $n"
[ "${n:-0}" -gt 1000 ] || problems="$problems storage-nearly-empty:$n"
if [ -z "$problems" ]; then
    echo "COMPLETENESS: ok (the per-container init check runs with the queries)"
else
    echo "COMPLETENESS: FAILED:$problems"
fi
