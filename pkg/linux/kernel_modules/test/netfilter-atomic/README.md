# Netfilter hook must not sleep

The audit module's netfilter hook logged each packet with
`kernel_helper_audit_log`, which calls `audit_log` with `GFP_KERNEL`. While the
audit backlog is full, `audit_log` then waits for room. A netfilter hook must
not sleep: it runs in an RCU read-side critical section and, for received
packets, in softirq context. The result is `BUG: scheduling while atomic`
followed by soft lockups and a frozen system.

It needs `nf_hooks` on, which SPADE's Audit reporter sets with
`networkAddressTranslation=true` (as in
`cfg/spade.reporter.Audit-with-namespace-awareness.config`), and a full audit
backlog, which a busy system or a slow audit consumer produces.

The fix adds `kernel_helper_audit_log_atomic`, which passes `GFP_ATOMIC`, and
uses it in the netfilter hook. With a full backlog the kernel drops the packet
record and counts it in the audit `lost` counter instead of waiting. The
syscall hooks run in process context and keep the blocking variant.

## Reproducing

`nf-repro.sh` loads a module build with `nf_hooks=1`, makes the backlog
overflow (limit 64, auditd stopped) and sends loopback TCP traffic:

```
./nf-repro.sh MODULE_DIR [CONNECTIONS]
```

Build the module before and after the fix (in `pkg/linux/kernel_modules`,
`make -f Kbuild PACKAGE_DIR=<dir> KDIR=<kernel headers> all`; the modules land
in `build/`) and run the script once for each. Use a disposable VM: without the
fix the guest freezes within a second and needs a power-off.

The guest freezes before journald can save the BUG report. To see it, send
kernel messages to a serial port as well: add `console=tty0
console=ttyS0,115200` to the kernel command line, point the VM's serial port
at a file on the host, and run `sudo dmesg -n 8` before the script.

## Results

Ubuntu 24.04.3, Linux 6.8.0-146-generic, VirtualBox, 16 vCPUs. Both modules
were built from the same tree, without and with the fix.

| Run | Without the fix | With the fix |
|---|---|---|
| 15 connections | 5 × `BUG: scheduling while atomic` in `nf_handle_packet`, an RCU warning, soft lockups (CPU stuck 78 s), an RCU stall; frozen | finished; 908 records lost; no BUG, WARNING or lockup |
| traffic for 10 s | frozen within about a second | 3306 connections; 235690 records lost; no BUG, WARNING or lockup |

The first report without the fix, from the serial console:

```
BUG: scheduling while atomic: python3/3715/0x00000102
Call Trace:
 __schedule_bug+0x64/0x80
 __schedule+0x69/0x6b0
 schedule_timeout+0x95/0x170
 audit_log_start+0x130/0x3d0
 audit_log+0x4c/0xc0
 kernel_helper_audit_log+0x117/0x130 [spade_audit]
 nf_handle_packet+0x24b/0x370 [spade_audit]
 kernel_netfilter_hook_first+0x3f/0x60 [spade_audit]
 nf_hook_slow+0x46/0x130
 ip_rcv+0x115/0x1a0
 net_rx_action+0x181/0x2e0
 handle_softirqs+0xdb/0x340
```

The same trace appeared without any test script, while SPADE recorded a
trace with `networkAddressTranslation=true`: the victim was PostgreSQL, the
SPADE storage, whose loopback traffic went through the hook while the backlog
was full.

Also noticed, not changed here: in `nf_handle_packet`, a packet that was
logged falls through to `discard_and_exit` and is counted as discarded too.
