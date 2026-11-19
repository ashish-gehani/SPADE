#!/usr/bin/env python3
"""Compare the labeled vertices and the process vertices of two query results.

Usage: compare-labels.py DIR_A DIR_B   (each a <trace>/<storage> directory of query.sh)
"""
import re
import sys

NODE = re.compile(r'^"([0-9a-f]+)" \[label="(.*?)" shape=')


def vertices(path):
    out = {}
    with open(path, errors='replace') as f:
        for line in f:
            m = NODE.match(line)
            if m:
                fields = dict(p.partition(':')[::2] for p in m.group(2).split('\\n'))
                out[m.group(1)] = fields.get('container_id')
    return out


def main():
    a, b = sys.argv[1], sys.argv[2]
    same = True
    for name in ('containers.dot', 'processes.dot'):
        va, vb = vertices(f'{a}/{name}'), vertices(f'{b}/{name}')
        only_a, only_b = va.keys() - vb.keys(), vb.keys() - va.keys()
        differ = [h for h in va.keys() & vb.keys() if va[h] != vb[h]]
        print(f'{name}: {len(va)} and {len(vb)} vertices; only in the first {len(only_a)}, '
              f'only in the second {len(only_b)}, with another label {len(differ)}')
        same = same and not (only_a or only_b or differ)
    print('the same' if same else 'DIFFERENT')


main()
