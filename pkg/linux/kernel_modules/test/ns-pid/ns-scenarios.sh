#!/bin/bash
# Scenarios for the ns pid A/B. Run as root, with the audit module loaded.
# Each scenario is bracketed by audit markers (auditctl -m), so module records
# can be attributed to a scenario by time.
#
#   S1 host fork                      baseline: ns_ns_pid must equal the host pid
#   S2 unshare --pid --fork (host)    9fd54e0c: the new namespace's first process is 1
#   S3 children forked inside an ns   control: creator inside the ns, both variants agree
#   S4 nsenter --pid (host caller)    9fd54e0c: setns then fork, child gets its in-ns pid
#   S5 unshare + nsenter from INSIDE  d445c7cc: the caller is not in the host namespace
#      an outer PID namespace
#   S6 docker run                     the real runc path
#   S7 docker exec                    runc's setns-then-fork path
set -u
mark() { auditctl -m "nsab:$1" >/dev/null 2>&1; }
gap() { sleep 0.5; }

mark "S1:host-fork:begin"
sh -c 'sleep 1; true'
mark "S1:host-fork:end"; gap

mark "S2:unshare-from-host:begin"
unshare --pid --fork sleep 2
mark "S2:unshare-from-host:end"; gap

mark "S3:children-inside-ns:begin"
unshare --pid --fork --mount-proc sh -c 'sleep 2 & sleep 2 & wait'
mark "S3:children-inside-ns:end"; gap

mark "S4:nsenter-from-host:begin"
unshare --pid --fork --mount-proc sleep 6 &
outer=$!
sleep 1
target=$(pgrep -P "$outer" | head -1)        # host pid of the new namespace's init
nsenter --target "$target" --pid -- sh -c 'sleep 1; true'
wait "$outer"
mark "S4:nsenter-from-host:end"; gap

mark "S5:unshare-and-setns-inside-ns:begin"
unshare --pid --fork --mount-proc sh -c '
    unshare --pid --fork sleep 4 &
    sleep 1
    inner=$(pgrep -n -x sleep)                # nested namespace init, outer-ns pid
    nsenter --target "$inner" --pid -- sh -c "sleep 1; true"
    wait'
mark "S5:unshare-and-setns-inside-ns:end"; gap

mark "S6:docker-run:begin"
docker run --rm alpine sh -c 'sleep 1; true'
mark "S6:docker-run:end"; gap

docker run -d --name nsab-target alpine sleep 30 >/dev/null
sleep 2
mark "S7:docker-exec:begin"
docker exec nsab-target sh -c 'sleep 1; true'
mark "S7:docker-exec:end"
docker rm -f nsab-target >/dev/null
