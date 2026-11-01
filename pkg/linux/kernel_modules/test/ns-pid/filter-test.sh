#!/bin/bash
# Regression check: the module's pids/ppids filters must keep matching HOST pids.
# They filter on the caller's current->pid and current->real_parent->pid. A
# container's init has host pid H but pid 1 in its own namespace, and forks a
# child every half second. For each module variant built by km-ab-build.sh:
#   none        no filter                  -> records
#   pids=H      ignore the init by host pid -> none if filters use host pids
#   pids=1      ignore host pid 1 (systemd) -> records if filters use host pids;
#               none would mean the filter matched the init's in-namespace pid
#   ppids=P     ignore children of the shim -> none (the init's parent is the shim)
# Output: $NSAB_WORK/filter/run.log (NSAB_WORK defaults to ~/ns-ab)
set -u
KM_ROOT="${KM_ROOT:-$HOME/km-ab}"
OUT="${NSAB_WORK:-$HOME/ns-ab}/filter"
rm -rf "$OUT"; mkdir -p "$OUT"
exec > >(tee "$OUT/run.log") 2>&1

sudo docker rm -f nsab-filter >/dev/null 2>&1
sudo docker run -d --name nsab-filter alpine sh -c 'while true; do ( : ); sleep 0.5; done' >/dev/null
sleep 2
H=$(sudo docker inspect -f '{{.State.Pid}}' nsab-filter)
P=$(ps -o ppid= -p "$H" | tr -d ' ')
I=$(sudo readlink /proc/"$H"/ns/pid | tr -dc '0-9')
echo "container init: host pid $H, NSpid $(sudo grep NSpid /proc/"$H"/status | cut -f2-), parent $P ($(ps -o comm= -p "$P")), pid ns $I"

for v in base fixed; do
    KM="$KM_ROOT/$v/pkg/linux/kernel_modules/build"
    for cfg in none "pids=$H" "pids=1" "ppids=$P"; do
        extra=""
        [ "$cfg" != none ] && extra="$cfg"
        for m in spade_audit_controller spade_audit; do lsmod | grep -q "^$m " && sudo rmmod "$m"; done
        tag="nsab:filter:$v:$cfg"
        sudo insmod "$KM/spade_audit.ko"
        sudo insmod "$KM/spade_audit_controller.ko" \
            nf_handle_user=0 nf_hooks=0 nf_hooks_log_all_ct=-1 net_io=0 namespaces=1 \
            pid_trace_mode=1 ppid_trace_mode=1 uid_trace_mode=1 \
            config_file=/opt/spade/audit/audit.config $extra
        sudo auditctl -m "$tag:begin"
        sleep 3
        sudo auditctl -m "$tag:end"
        sudo rmmod spade_audit_controller; sudo rmmod spade_audit
        n=$(sudo cat /var/log/audit/audit.log \
            | awk -v b="$tag:begin" -v e="$tag:end" 'index($0, b) {on = 1} on {print} index($0, e) {on = 0}' \
            | grep -c "ns_operation=ns_NEWPROCESS .*ns_inum_pid=$I ")
        printf '%-6s %-14s container NEWPROCESS records in 3 s: %s\n' "$v" "$cfg" "$n"
    done
done
sudo docker rm -f nsab-filter >/dev/null
