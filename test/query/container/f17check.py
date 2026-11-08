#!/usr/bin/env python3
"""F17: the namespace labels of the version an execve creates after an unshare or setns.

For every process that called unshare or setns and then execve (auditd's log:
the kernel module's ns_UNSHARE and ns_SETNS records, the execve SYSCALL
records), the execve version in PostgreSQL (same pid, its `start time` the
execve's time) should carry the namespaces of the last such record, not those
the process was created with (its ns_NEWPROCESS record), which is what the
reporter wrote before F17. Host processes do this all the time: systemd's
`(spawn)` unshares a mount namespace before it execs a service.

Usage: f17check.py AUDITD_SLICE     (PostgreSQL as trace.sh leaves it)
"""
import collections
import re
import subprocess
import sys

FIELDS = {'ns_inum_mnt': 'mount namespace', 'ns_inum_net': 'net namespace', 'ns_inum_pid': 'pid namespace',
          'ns_inum_pid_children': 'children pid namespace', 'ns_inum_ipc': 'ipc namespace',
          'ns_inum_cgroup': 'cgroup namespace', 'ns_inum_usr': 'user namespace'}
NS = re.compile(r'msg=audit\(([0-9.]+):(\d+)\): .*ns_operation=ns_(\w+) .*ns_host_pid=(\d+) (.*)$')
EXECVE = re.compile(r'^type=SYSCALL msg=audit\(([0-9.]+):(\d+)\): arch=\S+ syscall=59 success=yes .* pid=(\d+) ')

steps = collections.defaultdict(list)   # pid -> [(serial, time, op, {field: value})]
with open(sys.argv[1], errors='replace') as f:
    for line in f:
        m = NS.search(line)
        if m and m.group(3) in ('UNSHARE', 'SETNS', 'NEWPROCESS'):
            values = dict(re.findall(r'(ns_inum_\w+)=(-?\d+)', m.group(5)))
            steps[m.group(4)].append((int(m.group(2)), m.group(1), m.group(3), values))
            continue
        m = EXECVE.match(line)
        if m:
            steps[m.group(3)].append((int(m.group(2)), m.group(1), 'EXECVE', {}))

cases = []   # (pid, execve time, expected, created-with)
for pid, ev in steps.items():
    ev.sort()
    created, last_step, stepped = None, None, False
    for serial, t, op, values in ev:
        if op == 'NEWPROCESS':
            created, last_step, stepped = values, values, False
        elif op in ('UNSHARE', 'SETNS'):
            last_step, stepped = values, True
        elif op == 'EXECVE' and stepped:
            cases.append((pid, t, last_step, created))
            stepped = False   # the next execve without a namespace step is not a case


def version(pid, t):
    cols = ', '.join(f'"{c}"' for c in FIELDS.values())
    sql = f'select {cols} from vertex where type = \'Process\' and pid = \'{pid}\' and "start time" = \'{t}\''
    out = subprocess.run(['psql', '-h', 'localhost', '-U', 'spade', '-d', 'spadedb', '-tA', '-F', '\t', '-c', sql],
                         capture_output=True, text=True, env={'PGPASSWORD': '12345', 'PATH': '/usr/bin:/bin'}).stdout
    rows = [r.split('\t') for r in out.strip().split('\n') if r]
    return [dict(zip(FIELDS.values(), r)) for r in rows]


verdict = collections.Counter()
examples = []
for pid, t, expected, created in cases:
    rows = version(pid, t)
    if not rows:
        verdict['no execve version in the graph'] += 1
        continue
    v = rows[0]
    want = {FIELDS[k]: x for k, x in expected.items() if k in FIELDS}
    if all(v.get(c) == x for c, x in want.items()):
        differs = created and any(v.get(FIELDS[k]) != x for k, x in created.items() if k in FIELDS)
        verdict['labels of the namespace step' + (', not the creation\'s' if differs else ' (same as the creation\'s)')] += 1
    else:
        bad = {c: (v.get(c), x) for c, x in want.items() if v.get(c) != x}
        verdict['WRONG labels'] += 1
        if len(examples) < 5:
            examples.append((pid, t, bad))
print(f'{len(cases)} execve calls after an unshare or setns:')
for k, n in verdict.most_common():
    print(f'  {n:5d}  {k}')
for e in examples:
    print('    e.g. pid', e[0], 'at', e[1], '(graph, expected):', e[2])
