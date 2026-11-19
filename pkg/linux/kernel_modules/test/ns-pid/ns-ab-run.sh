#!/bin/bash
# Run the ns pid scenarios under one kernel-module variant built by km-ab-build.sh.
# Usage: ns-ab-run.sh base|fixed
# Output: $NSAB_WORK/<variant>/ (NSAB_WORK defaults to ~/ns-ab): audit-slice.log, sampler.jsonl, run.log
set -u
V=${1:-}
case "$V" in base|fixed) ;; *) echo "usage: ns-ab-run.sh base|fixed"; exit 2;; esac
HERE=$(cd "$(dirname "$0")" && pwd)
KM="${KM_ROOT:-$HOME/km-ab}/$V/pkg/linux/kernel_modules/build"
OUT="${NSAB_WORK:-$HOME/ns-ab}/$V"

rm -rf "$OUT"
mkdir -p "$OUT"
exec > >(tee "$OUT/run.log") 2>&1
echo "variant=$V  start=$(date -Is)  kernel=$(uname -r)"

[ -f "$KM/spade_audit.ko" ] && [ -f "$KM/spade_audit_controller.ko" ] \
    || { echo "ERROR: modules missing under $KM (run km-ab-build.sh first)"; exit 1; }

# Everything that must not appear in the trace happens before the module loads.
sudo docker image inspect alpine >/dev/null 2>&1 || sudo docker pull -q alpine
for m in spade_audit_controller spade_audit; do
    lsmod | grep -q "^$m " && sudo rmmod "$m"
done
sudo auditctl -b 65536 >/dev/null          # until the next reboot; avoids drops in bursts
echo "audit before: $(sudo auditctl -s | grep -E '^(enabled|lost|backlog_limit) ' | tr '\n' ' ')"
echo "containers running: $(sudo docker ps -q | wc -l)"

sudo python3 "$HERE/ns-sampler.py" "$OUT/sampler.jsonl" 0.05 &
SAMPLER=$!
sleep 1

# The parameters SPADE's KernelModuleArgument passes by default, with namespaces on.
# The module only stores config_file; it never opens it.
sudo auditctl -m "nsab:$V:begin"
sudo insmod "$KM/spade_audit.ko"
sudo insmod "$KM/spade_audit_controller.ko" \
    nf_handle_user=0 nf_hooks=0 nf_hooks_log_all_ct=-1 net_io=0 namespaces=1 \
    pid_trace_mode=1 ppid_trace_mode=1 uid_trace_mode=1 \
    config_file=/opt/spade/audit/audit.config
echo "loaded: $(lsmod | awk '/^spade/{print $1}' | tr '\n' ' ')"
sleep 1

sudo bash "$HERE/ns-scenarios.sh"

sleep 1
sudo rmmod spade_audit_controller
sudo rmmod spade_audit
sudo auditctl -m "nsab:$V:end"
sleep 1
sudo kill -TERM "$SAMPLER"
wait "$SAMPLER" 2>/dev/null
echo "audit after:  $(sudo auditctl -s | grep -E '^(enabled|lost|backlog_limit) ' | tr '\n' ' ')"

# This run's slice of the audit log (the rotated file is included in case auditd rotated mid-run).
sudo cat /var/log/audit/audit.log.1 /var/log/audit/audit.log 2>/dev/null \
    | awk -v b="nsab:$V:begin" -v e="nsab:$V:end" \
          'index($0, b) {on = 1} on {print} index($0, e) {on = 0}' > "$OUT/audit-slice.log"
echo "slice: $(wc -l < "$OUT/audit-slice.log") lines, $(grep -c 'ns_subtype=ns_namespaces' "$OUT/audit-slice.log") namespace records"
echo "sampler: $(wc -l < "$OUT/sampler.jsonl") processes"
echo "done $(date -Is)"
