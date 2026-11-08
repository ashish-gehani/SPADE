#!/usr/bin/env python3
"""Summarize a SPADE .dot export: processes, artifacts and edges.

Usage: dotsum.py FILE.dot [--edges]
Processes are listed in time order with the namespace labels the ContainerLabels transformer reads.
"""
import re
import sys
from collections import Counter

NODE = re.compile(r'^"([0-9a-f]+)" \[label="(.*?)" shape=')
EDGE = re.compile(r'^"([0-9a-f]+)" -> "([0-9a-f]+)" \[label="(.*?)"')


def labels(text):
    out = {}
    for part in text.split('\\n'):
        key, sep, value = part.partition(':')
        if sep:
            out[key] = value
    return out


def read(path):
    nodes, edges = {}, []
    with open(path, encoding='utf-8', errors='replace') as f:
        for line in f:
            m = NODE.match(line)
            if m:
                nodes[m.group(1)] = labels(m.group(2))
                continue
            m = EDGE.match(line)
            if m:
                edges.append((m.group(1), m.group(2), labels(m.group(3))))
    return nodes, edges


def when(v):
    return v.get('start time') or ('seen ' + v.get('seen time', '?'))


def main():
    path = sys.argv[1]
    nodes, edges = read(path)
    procs = {h: v for h, v in nodes.items() if v.get('type') == 'Process'}
    arts = {h: v for h, v in nodes.items() if v.get('type') == 'Artifact'}
    print(f'{path}: {len(nodes)} vertices ({len(procs)} processes, {len(arts)} artifacts), {len(edges)} edges')
    print('  processes (time | name | pid ppid | pidns | children pidns | ns pid | hash):')
    for h, v in sorted(procs.items(), key=lambda kv: when(kv[1])):
        print(f"    {when(v)} | {v.get('name')} | {v.get('pid')} {v.get('ppid')} | "
              f"{v.get('pid namespace')} | {v.get('children pid namespace')} | {v.get('ns pid')} | {h[:8]}")
    kinds = Counter(v.get('subtype', '?') for v in arts.values())
    print('  artifacts by subtype:', dict(kinds))
    for h, v in arts.items():
        if v.get('subtype') == 'network socket':
            print('    socket:', {k: v[k] for k in v if k in ('local address', 'local port', 'remote address', 'remote port', 'protocol')})
    ops = Counter((e[2].get('type'), e[2].get('operation')) for e in edges)
    print('  edges by type/operation:', dict(sorted(ops.items(), key=lambda kv: -kv[1])))
    if '--edges' in sys.argv:
        for c, p, e in sorted(edges, key=lambda x: x[2].get('time', '')):
            if e.get('type') in ('WasTriggeredBy',):
                cn, pn = nodes.get(c, {}), nodes.get(p, {})
                print(f"    {e.get('time')} {e.get('operation')} flags={e.get('flags', '')}: "
                      f"{pn.get('name')}({pn.get('pid')},{p[:6]}) -> {cn.get('name')}({cn.get('pid')},{c[:6]})")


if __name__ == '__main__':
    main()
