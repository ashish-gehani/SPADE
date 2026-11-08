#!/usr/bin/env python3
"""Ground truth for the ns pid A/B, sampled from the host's /proc.

Every INTERVAL seconds, records each process (keyed by host pid and start
time) the first time it is seen, with:
  nspid             the NSpid line: the pid in every PID namespace from the
                    host's down to the process's own (last entry = own pid)
  pid_ns            its PID namespace inode
  pid_for_children  every distinct pid_for_children inode seen over its life
                    (changes after unshare(CLONE_NEWPID) or setns)
plus every name it had (a child keeps its parent's name until execve), ppid
and the first/last time the sampler saw it.

Runs until SIGTERM or SIGINT, then writes one JSON object per process.
Usage: ns-sampler.py OUT.jsonl [INTERVAL_SECONDS]
"""
import json
import os
import signal
import sys
import time

out_path = sys.argv[1]
interval = float(sys.argv[2]) if len(sys.argv) > 2 else 0.05

procs = {}
stopping = False


def handle_signal(*_):
    global stopping
    stopping = True


signal.signal(signal.SIGTERM, handle_signal)
signal.signal(signal.SIGINT, handle_signal)


def ns_inode(pid, kind):
    link = os.readlink(f'/proc/{pid}/ns/{kind}')   # e.g. "pid:[4026531836]"
    return int(link[link.index('[') + 1:-1])


def sample():
    now = time.time()
    for entry in os.listdir('/proc'):
        if not entry.isdigit():
            continue
        try:
            with open(f'/proc/{entry}/stat', 'rb') as f:
                stat = f.read()
            # The command name may contain spaces or parentheses: split after the last ')'.
            rest = stat[stat.rindex(b')') + 2:].split()
            ppid, start_ticks = int(rest[1]), int(rest[19])
            key = f'{entry}:{start_ticks}'
            pfc = ns_inode(entry, 'pid_for_children')
            rec = procs.get(key)
            if rec is not None:
                if pfc not in rec['pid_for_children']:
                    rec['pid_for_children'].append(pfc)
                # A forked child keeps its parent's name until it calls execve.
                with open(f'/proc/{entry}/comm') as f:
                    comm = f.read().strip()
                if comm not in rec['names']:
                    rec['names'].append(comm)
                rec['last_seen'] = now
                continue
            name = nspid = None
            with open(f'/proc/{entry}/status') as f:
                for line in f:
                    if line.startswith('Name:'):
                        name = line.split(None, 1)[1].strip()
                    elif line.startswith('NSpid:'):
                        nspid = [int(x) for x in line.split()[1:]]
            procs[key] = {
                'host_pid': int(entry), 'ppid': ppid, 'names': [name], 'nspid': nspid,
                'pid_ns': ns_inode(entry, 'pid'), 'pid_for_children': [pfc],
                'start_ticks': start_ticks, 'first_seen': now, 'last_seen': now,
            }
        except (FileNotFoundError, ProcessLookupError, PermissionError, ValueError):
            continue   # the process exited while being read


while not stopping:
    sample()
    time.sleep(interval)

with open(out_path, 'w') as f:
    for rec in procs.values():
        f.write(json.dumps(rec) + '\n')
print(f'sampler: {len(procs)} processes recorded', file=sys.stderr)
