#!/bin/bash
# Does the audit module record a process created with clone3? Before cea5d23c
# it hooked clone, fork and vfork only. One child is created via posix_spawn
# (glibc tries clone3 first) and one via fork (glibc uses clone); strace shows
# which syscall made each, and its flags. For clone3 the record also carries
# ns_clone_flags: clone_args.flags with the exit signal in the low byte.
# Usage: clone3-test.sh [base|fixed]   (default fixed)
# Output: $NSAB_WORK/clone3/ (NSAB_WORK defaults to ~/ns-ab)
set -u
V=${1:-fixed}
KM="${KM_ROOT:-$HOME/km-ab}/$V/pkg/linux/kernel_modules/build"
OUT="${NSAB_WORK:-$HOME/ns-ab}/clone3"
rm -rf "$OUT"; mkdir -p "$OUT"
exec > >(tee "$OUT/run.log") 2>&1
command -v strace >/dev/null || { echo "ERROR: strace is required"; exit 1; }

for m in spade_audit_controller spade_audit; do lsmod | grep -q "^$m " && sudo rmmod "$m"; done
sudo auditctl -m "nsab:clone3:begin"
sudo insmod "$KM/spade_audit.ko"
sudo insmod "$KM/spade_audit_controller.ko" \
    nf_handle_user=0 nf_hooks=0 nf_hooks_log_all_ct=-1 net_io=0 namespaces=1 \
    pid_trace_mode=1 ppid_trace_mode=1 uid_trace_mode=1 config_file=/opt/spade/audit/audit.config
sleep 1

children=$(strace -f -e trace=clone,clone3,fork,vfork -o "$OUT/strace.txt" python3 -c '
import os
spawned = os.posix_spawn("/bin/true", ["true"], {})
os.waitpid(spawned, 0)
forked = os.fork()
if forked == 0:
    os._exit(0)
os.waitpid(forked, 0)
print(spawned, forked)')
read -r SPAWNED FORKED <<< "$children"

sleep 1
sudo rmmod spade_audit_controller; sudo rmmod spade_audit
sudo auditctl -m "nsab:clone3:end"

echo "--- strace (which syscall created each child):"
grep -E "clone3?\(|fork\(" "$OUT/strace.txt"
records=$(sudo cat /var/log/audit/audit.log \
    | awk 'index($0, "nsab:clone3:begin") {on = 1} on {print} index($0, "nsab:clone3:end") {on = 0}' \
    | grep -oE 'ns_syscall=[0-9]+ .*ns_operation=ns_NEWPROCESS ns_ns_pid=[0-9]+ ns_host_pid=[0-9]+ .*')
echo "--- NEWPROCESS records for the two children:"
for c in "posix_spawn:$SPAWNED" "fork:$FORKED"; do
    how=${c%%:*}; pid=${c#*:}
    rec=$(echo "$records" | grep -E "ns_host_pid=$pid( |\$)")
    if [ -n "$rec" ]; then
        r="recorded, ns_syscall=$(echo "$rec" | grep -oE '^ns_syscall=[0-9]+' | cut -d= -f2)"
        flags=$(echo "$rec" | grep -oE 'ns_clone_flags=[0-9]+' | cut -d= -f2)
        [ -n "$flags" ] && r="$r, ns_clone_flags=$(printf '0x%x' "$flags")"
    else
        r="NO RECORD"
    fi
    printf '%-12s child %-7s %s\n' "$how" "$pid" "$r"
done
