#!/bin/bash
# Reproduce "BUG: scheduling while atomic" in the audit module's netfilter hook,
# or show that a module build no longer has it. See README.md.
#
# The hook logs every packet. If it may sleep, audit_log waits for room while
# the audit backlog is full, which a netfilter hook must never do. To get there:
#   1. load the module with nf_hooks=1
#   2. make the backlog overflow: limit 64 and auditd stopped, so nothing
#      drains; wait time 1, so the kernel does try to wait but syscalls that
#      wait stay quick
#   3. send loopback TCP traffic: received packets run the hook in softirq
#
# Usage: nf-repro.sh MODULE_DIR [CONNECTIONS]
#   MODULE_DIR   directory with spade_audit.ko and spade_audit_controller.ko
#   CONNECTIONS  number of loopback connections (default: as many as fit in 10 s)
# Restores auditd and the backlog settings at the end, if the system survives.
set -u
KM=${1:?usage: nf-repro.sh MODULE_DIR [CONNECTIONS]}
CONNS=${2:-0}
[ -f "$KM/spade_audit.ko" ] && [ -f "$KM/spade_audit_controller.ko" ] \
    || { echo "ERROR: spade_audit.ko or spade_audit_controller.ko missing in $KM"; exit 1; }
echo "modules=$KM  kernel=$(uname -r)  start=$(date -Is)"

for m in spade_audit_controller spade_audit; do lsmod | grep -q "^$m " && sudo rmmod "$m"; done
sudo dmesg -C
echo "audit before: $(sudo auditctl -s | grep -E '^(lost|backlog_limit|backlog_wait_time) ' | tr '\n' ' ')"
LIMIT=$(sudo auditctl -s | awk '$1 == "backlog_limit" {print $2}')
WAIT=$(sudo auditctl -s | awk '$1 == "backlog_wait_time" {print $2}')

sudo insmod "$KM/spade_audit.ko"
sudo insmod "$KM/spade_audit_controller.ko" \
    nf_handle_user=0 nf_hooks=1 nf_hooks_log_all_ct=-1 net_io=0 namespaces=0 \
    pid_trace_mode=1 ppid_trace_mode=1 uid_trace_mode=1 config_file=/opt/spade/audit/audit.config

sudo auditctl -b 64 >/dev/null
sudo auditctl --backlog_wait_time 1 >/dev/null
AUDITD=$(pidof auditd)
sudo kill -STOP "$AUDITD"
echo "auditd stopped; sending loopback TCP traffic"

NF_CONNS="$CONNS" python3 - <<'PY'
import os, socket, threading, time
limit = int(os.environ['NF_CONNS'])
srv = socket.socket()
srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
srv.bind(('127.0.0.1', 18999))
srv.listen(128)
def serve():
    while True:
        c, _ = srv.accept()
        c.recv(64)
        c.close()
threading.Thread(target=serve, daemon=True).start()
end, n = time.time() + 10, 0
while (n < limit) if limit else (time.time() < end):
    s = socket.create_connection(('127.0.0.1', 18999))
    s.sendall(b'x')
    s.close()
    n += 1
print('loopback connections:', n)
PY

sudo kill -CONT "$AUDITD"
sleep 2
sudo auditctl -b "$LIMIT" >/dev/null
sudo auditctl --backlog_wait_time "$WAIT" >/dev/null
sudo rmmod spade_audit_controller
sudo rmmod spade_audit
echo "audit after:  $(sudo auditctl -s | grep -E '^(lost|backlog_limit|backlog_wait_time) ' | tr '\n' ' ')"
echo "kernel BUG/WARNING/lockup lines: $(sudo dmesg | grep -cE 'BUG:|WARNING:|soft lockup|rcu.*stall')"
echo "backlog overflow notices: $(sudo dmesg | grep -c 'audit_backlog=')"
