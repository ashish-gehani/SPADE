#!/usr/bin/env python3
"""Check trace 1's query results against the Part B expectations (B1-B7).

Reads ~/stage-b/trace1/{truth.txt,run.log,<storage>/client.log,<storage>/*.dot}
for every storage directory present, and prints, per scenario, what each
storage returned and how it compares with the kernel's ground truth. The
expectations themselves are in TRACE_VALIDATION.md, Part B; this script only
collects the facts to judge them by.

Usage: check-trace1.py [TRACE_DIR]
"""
import os
import re
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dotsum import read  # noqa: E402

T = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser('~/stage-b/trace1')
STORAGES = [s for s in ('PostgreSQL', 'Neo4j', 'Quickstep') if os.path.isdir(os.path.join(T, s))]
HOST = '4026531836'


def truth():
    out = {}
    for line in open(os.path.join(T, 'truth.txt')):
        key = line.split()[0] + (' ' + line.split()[2] if line.startswith('B6') else '')
        out[key] = dict(re.findall(r'(\w+)=(\d+)', line))
    return out


def queries(storage):
    """name -> (call, error or None) from the client log"""
    out, name = {}, None
    log = open(os.path.join(T, storage, 'client.log'), errors='replace').read().split('\n')
    for i, line in enumerate(log):
        m = re.match(r'^-> \$(\w+) = \$base\.(.*)$', line)
        if m:
            name = m.group(1)
            err = None
            if i + 1 < len(log) and log[i + 1].startswith('Error'):
                j, lines = i + 1, []
                while j < len(log) and log[j].strip() and not log[j].startswith('->'):
                    lines.append(log[j])
                    j += 1
                err = '\n'.join(lines)
            out[name] = (m.group(2), err)
    return out


def graph(storage, name):
    path = os.path.join(T, storage, name + '.dot')
    return read(path) if os.path.exists(path) else ({}, [])


def procs(nodes):
    return {h: v for h, v in nodes.items() if v.get('type') == 'Process'}


def label(v):
    return f"{v.get('name')}({v.get('pid')}, ns {v.get('pid namespace')}>{v.get('children pid namespace')})"


def starts(nodes, edges):
    """creation edges into a process with ns pid 1 in a container namespace"""
    out = []
    for c, p, e in edges:
        if e.get('operation') not in ('fork', 'clone', 'vfork'):
            continue
        child, parent = nodes.get(c, {}), nodes.get(p, {})
        if child.get('ns pid') == '1' and child.get('pid namespace') not in (HOST, '-1', None):
            out.append((e.get('time'), child, parent, e.get('flags', '')))
    return sorted(out, key=lambda x: x[0])


def main():
    tr = truth()
    print(f'trace: {T}\nstorages: {", ".join(STORAGES)}\ntruth: ' + '; '.join(
        f'{k} {v}' for k, v in tr.items()))
    q = {s: queries(s) for s in STORAGES}

    print('\n== Same results on every storage')
    names = sorted(set().union(*(set(q[s]) for s in STORAGES)))
    for n in names:
        sets = {}
        for s in STORAGES:
            nodes, edges = graph(s, n)
            sets[s] = (frozenset(nodes), len(edges), (q[s].get(n) or (None, None))[1])
        same = len({v for v in sets.values()}) == 1
        first = next(iter(sets.values()))
        print(f'  {n:16s} {"same" if same else "DIFFER"}: {len(first[0])} vertices, {first[1]} edges'
              + (', error' if first[2] else ''))
        if not same:
            for s, v in sets.items():
                print(f'      {s}: {len(v[0])} vertices, {v[1]} edges, error={bool(v[2])}')

    s = STORAGES[0]
    print(f'\n== getContainerInit ({s})')
    nodes, edges = graph(s, 'init')
    st = starts(nodes, edges)
    print(f'  {len(st)} starts; by namespace: {dict(Counter(c.get("pid namespace") for _, c, _, _ in st))}')
    for t, child, parent, flags in st:
        print(f'    {t} {label(parent)} -> {label(child)} flags={flags}')
    names_in = Counter(v.get('name') for v in procs(nodes).values())
    print(f'  process names in the result: {dict(names_in)}')
    ops = Counter(e.get('operation') for _, _, e in edges)
    print(f'  edge operations: {dict(ops)}')
    arts = [v for v in nodes.values() if v.get('type') != 'Process']
    print(f'  non-process vertices: {len(arts)}')

    print('\n== Numbered containers (B6 and B2)')
    for n, (call, err) in q[s].items():
        if err and 'belonged to' in err and 'Pick one' in err:
            print(f'  {call}:')
            for line in err.split('\n')[1:]:
                print(f'      {line.strip()}')

    print('\n== Boundaries')
    for n, (call, err) in q[s].items():
        if n in ('init', 'all') or err:
            continue
        nodes, edges = graph(s, n)
        ps = procs(nodes)
        inside = [v for v in ps.values() if v.get('pid namespace') not in (HOST, '-1')]
        outside = [v for v in ps.values() if v.get('pid namespace') in (HOST, '-1')]
        socks = {h: v for h, v in nodes.items() if v.get('subtype') == 'network socket'}
        print(f'  {call}: {len(ps)} processes ({len(inside)} in containers, by namespace '
              f'{dict(Counter(v.get("pid namespace") for v in inside))}); neighbours outside: '
              f'{dict(Counter(v.get("name") for v in outside))}; network sockets: {len(socks)}')
        print(f'      processes inside: {dict(Counter(v.get("name") for v in inside))}')
        for h, sock in socks.items():
            linked = sorted({nodes[o].get('name') for c, p, _ in edges for a, o in ((c, p), (p, c))
                             if a == h and o in nodes})
            print(f'      socket {sock.get("remote address")}:{sock.get("remote port")} linked to {linked}')


if __name__ == '__main__':
    main()
