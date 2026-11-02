#!/usr/bin/env python3
"""Ground truth for trace 3: which processes ran in which Docker container.

Every INTERVAL seconds, reads each process's /proc/<pid>/status and
/proc/<pid>/cgroup. A process in a cgroup `.../docker-<id>.scope` belongs to
container <id>. Run it as the user SPADE runs as: the audit rules leave that
user out, so the sampler adds nothing to the trace, and status and cgroup are
readable for every process (the ns/ links are not, for other users).

Records each process (keyed by host pid and start time) the first time it is
seen: container id, names, ppid, NSpid, first and last time seen.
Usage: cgroup-sampler.py OUT.jsonl [INTERVAL_SECONDS]   Stops on SIGTERM/SIGINT.
"""
import json
import os
import re
import signal
import sys
import time

out_path = sys.argv[1]
interval = float(sys.argv[2]) if len(sys.argv) > 2 else 0.1
SCOPE = re.compile(r'docker-([0-9a-f]{64})\.scope')
procs = {}
stopping = False


def stop(*_):
    global stopping
    stopping = True


signal.signal(signal.SIGTERM, stop)
signal.signal(signal.SIGINT, stop)

while not stopping:
    now = time.time()
    for entry in os.listdir('/proc'):
        if not entry.isdigit():
            continue
        try:
            with open(f'/proc/{entry}/cgroup') as f:
                m = SCOPE.search(f.read())
            if not m:
                continue
            with open(f'/proc/{entry}/stat', 'rb') as f:
                stat = f.read()
            start_ticks = int(stat[stat.rindex(b')') + 2:].split()[19])
            key = f'{entry}:{start_ticks}'
            fields = {}
            with open(f'/proc/{entry}/status') as f:
                for line in f:
                    k, _, v = line.partition(':')
                    if k in ('Name', 'PPid', 'NSpid'):
                        fields[k] = v.strip()
        except (FileNotFoundError, ProcessLookupError, PermissionError, ValueError, IndexError):
            continue   # exited while being read
        rec = procs.get(key)
        if rec is None:
            procs[key] = {'host_pid': int(entry), 'container': m.group(1), 'names': [fields.get('Name')],
                          'ppid': int(fields.get('PPid', 0)),
                          'nspid': [int(x) for x in fields.get('NSpid', '').split()],
                          'first_seen': now, 'last_seen': now}
        else:
            rec['last_seen'] = now
            if fields.get('Name') not in rec['names']:
                rec['names'].append(fields.get('Name'))
    time.sleep(interval)

with open(out_path, 'w') as f:
    for rec in procs.values():
        f.write(json.dumps(rec) + '\n')
print(f'cgroup sampler: {len(procs)} container processes', file=sys.stderr)
