#!/usr/bin/env python3
"""B12: the Audit reporter's descriptor warnings in a trace, by process.

Counts the reporter's three warnings about descriptors it does not know
("No FD with number", "Missing directory FD", "Expected FD to be of type
directory") and names the process of each from auditd's log, by the event's
serial number. nofd.py then tells which "No FD" closes are gaps in SPADE.

Usage: fdcheck.py SPADE_LOG AUDITD_SLICE
"""
import collections
import re
import sys

log, slice_path = sys.argv[1], sys.argv[2]
KINDS = ('No FD with number', 'Missing directory FD', 'Expected FD to be of type directory')
EVENT = re.compile(r'\[Time:EventID=[0-9.]+:(\d+), SYSCALL=(\w+)\] (.*)$')
found = []   # (kind, serial, syscall)
for line in open(log, errors='replace'):
    m = EVENT.search(line)
    if not m:
        continue
    for k in KINDS:
        if m.group(3).startswith(k):
            found.append((k, m.group(1), m.group(2)))
serials = {s for _, s, _ in found}

SYSCALL = re.compile(r'^type=SYSCALL msg=audit\([0-9.]+:(\d+)\): .* pid=(\d+) .* comm=("[^"]*"|[0-9A-F]+) ')
who = {}
with open(slice_path, errors='replace') as f:
    for line in f:
        if not line.startswith('type=SYSCALL'):
            continue
        m = SYSCALL.match(line)
        if m and m.group(1) in serials:
            c = m.group(3)
            c = c.strip('"') if c.startswith('"') else bytes.fromhex(c).decode(errors='replace')
            who[m.group(1)] = c

for k in KINDS:
    rows = [(s, sc) for kk, s, sc in found if kk == k]
    by = collections.Counter(who.get(s, '?') for s, _ in rows)
    print(f'{k}: {len(rows)}')
    print(f'    by process: {dict(by.most_common(10))}')
    print(f'    by syscall: {dict(collections.Counter(sc for _, sc in rows).most_common(6))}')
