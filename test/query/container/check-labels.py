#!/usr/bin/env python3
"""Check the ContainerLabels transformer's labels on a recorded trace.

Ground truth: the kernel module's NEWPROCESS records in auditd's log
(auditd-slice.log), which Part A checked against the kernel: every process
created in the trace, with its host pid, its pid in its own namespace and its
PID namespace. A container run is a record with ns pid 1 in a namespace other
than the host's; runs of a reused ID are told apart by time. For traces 3 and 4,
sampler.jsonl also lists the processes the cgroup sampler saw in each container.

Results: <storage>/{containers,processes,sockets}.dot and times.tsv from query.sh.

  T1  one container_id per kernel run whose start is in the trace, naming the
      run's namespace and its init's creation
  T2  every process version that the kernel recorded as created in a run (and
      every sampled process) is labeled with that run's container_id
  T3  nothing else is labeled: no host process, no process of a container
      started before tracing, no process of another run
  T4  a labeled socket's processes all have its label and its net namespace;
      why the other sockets next to container processes are left
  T5  the same labels on every storage
  T6  how long the transform took

Usage: check-labels.py [TRACE_DIR]
"""
import json
import os
import re
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dotsum import read  # noqa: E402

T = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser('~/stage-b/trace4')
STORAGES = [s for s in ('PostgreSQL', 'Neo4j', 'Quickstep')
            if os.path.exists(os.path.join(T, s, 'processes.dot'))]
HOST = '4026531836'
NEWPROC = re.compile(r'msg=audit\((\d+\.\d+):\d+\): .*ns_operation=ns_NEWPROCESS ns_ns_pid=(\d+) '
                     r'ns_host_pid=(\d+) .*ns_inum_pid=(\d+) ')


def kernel_truth():
    """host pid -> [(time, ns pid, X)] in time order"""
    procs = defaultdict(list)
    with open(os.path.join(T, 'auditd-slice.log'), errors='replace') as f:
        for line in f:
            if 'ns_NEWPROCESS' in line:
                m = NEWPROC.search(line)
                if m:
                    procs[m.group(3)].append((float(m.group(1)), m.group(2), m.group(4)))
    for recs in procs.values():
        recs.sort()
    return procs


def scenarios():
    """namespace ID -> scenario names, from truth.txt (traces 1 and 2)"""
    out = defaultdict(set)
    path = os.path.join(T, 'truth.txt')
    if os.path.exists(path):
        for line in open(path):
            words = line.split()
            if words and re.match(r'^B\d+', words[0]):
                for x in re.findall(r'\b[XY]=(\d+)', line):
                    out[x].add(words[0])
    return out


def sampled():
    path = os.path.join(T, 'sampler.jsonl')
    return [json.loads(line) for line in open(path)] if os.path.exists(path) else []


def times(storage):
    out = []
    path = os.path.join(T, storage, 'times.tsv')
    if os.path.exists(path):
        for line in open(path):
            parts = line.rstrip('\n').split('\t')
            if len(parts) >= 2 and 'transform' in parts[-1]:
                out.append(f'{parts[0]} s')
    return out


def errors(storage):
    path = os.path.join(T, storage, 'client.log')
    if not os.path.exists(path):
        return ['no client.log']
    return [line.strip() for line in open(path, errors='replace') if line.startswith('Error')]


def when(v):
    return v.get('start time') or v.get('seen time') or ''


def main():
    procs = kernel_truth()
    where = scenarios()
    samples = sampled()

    inits = sorted((t, h, x) for h, recs in procs.items() for (t, nspid, x) in recs if nspid == '1' and x != HOST)
    runs_of_x = defaultdict(list)
    for t, h, x in inits:
        runs_of_x[x].append((t, h))

    def run_of(h, t):
        """(X, run number or None) of the process with host pid h at time t, from its NEWPROCESS record"""
        recs = [r for r in procs.get(h, []) if r[0] <= t + 1.0]
        if not recs:
            return None
        x = recs[-1][2]
        if x == HOST:
            return (x, None)
        n = None
        for i, (ti, _) in enumerate(runs_of_x.get(x, []), 1):
            if ti <= recs[-1][0] + 0.001:
                n = i
        return (x, n)

    def run_label(x, n):
        names = ','.join(sorted(where.get(x, []))) or ''
        return f'{x} run {n}' + (f' ({names})' if names else '')

    print(f'trace: {T}\nstorages: {", ".join(STORAGES)}')
    print(f'kernel: {len(procs)} processes created, {len(inits)} container runs in {len(runs_of_x)} namespaces, '
          f'reused IDs: { {x: len(r) for x, r in runs_of_x.items() if len(r) > 1} }; sampled: {len(samples)}')
    if not STORAGES:
        print('no query results')
        return

    results = {}
    for s in STORAGES:
        results[s] = (read(os.path.join(T, s, 'containers.dot'))[0], read(os.path.join(T, s, 'processes.dot'))[0],
                      read(os.path.join(T, s, 'sockets.dot')))

    print('\n== T5: same labels on every storage; T6: transform time')
    sigs = {}
    for s in STORAGES:
        containers = results[s][0]
        sigs[s] = frozenset((h, v.get('container_id')) for h, v in containers.items())
        print(f'  {s}: {len(containers)} labeled vertices, {len({v.get("container_id") for v in containers.values()})} '
              f'container ids; transform {", ".join(times(s)) or "?"}; errors: {errors(s) or "none"}')
    print(f'  {"the same on every storage" if len(set(sigs.values())) == 1 else "DIFFERENT between storages"}')

    s0 = STORAGES[0]
    containers, processes, (snodes, sedges) = results[s0]

    print(f'\n== T1: containers ({s0})')
    ids = sorted({v['container_id'] for v in containers.values()})
    id_of_run = {}
    for cid in ids:
        x, t, event = cid.split(':')
        # the run whose init record is nearest in time: the clone and the module's record of it
        # are one audit event, so they have the same time; runs of a reused ID can be < 1 s apart
        near = sorted((abs(ti - float(t)), n, ti, h) for n, (ti, h) in enumerate(runs_of_x.get(x, []), 1))
        match = [(n, ti, h) for d, n, ti, h in near[:1] if d < 0.01]
        if len(match) == 1:
            n, ti, h = match[0]
            init = [v for v in containers.values() if v.get('container_id') == cid and v.get('pid') == h
                    and v.get('type') == 'Process']
            id_of_run[(x, n)] = cid
            print(f'  {cid}: {run_label(x, n)}, init host pid {h}'
                  f'{"" if init else "  <-- the init is not labeled"}')
        else:
            print(f'  {cid}: NO kernel run matches ({len(match)} candidates)  <--')
    for x, runs in runs_of_x.items():
        for n, (ti, h) in enumerate(runs, 1):
            if (x, n) not in id_of_run:
                print(f'  kernel run {run_label(x, n)} at {ti}, init {h}: NO container_id  <--')
    print(f'  {len(ids)} container ids, {len(inits)} kernel runs, {len(id_of_run)} matched')

    print(f'\n== T2, T3: process versions ({s0})')
    verdict = Counter()
    problems = defaultdict(list)
    for h, v in processes.items():
        if v.get('type') != 'Process':
            continue
        label = v.get('container_id')
        ns = v.get('pid namespace')
        k = run_of(v.get('pid'), float(when(v) or 0))
        expected = None
        why = ''
        if ns in (HOST, '-1', '4294967295', None):
            why = 'host or unobserved'
        elif k is None:
            why = 'not created in the trace (no NEWPROCESS record)'
        elif k[0] != ns:
            why = f'its NEWPROCESS record names {k[0]}'
        elif k[1] is None:
            why = 'in a namespace whose run started before tracing'
        else:
            expected = id_of_run.get(k)
            why = f'{run_label(*k)}'
        first_seen = 'start time' not in v
        if label == expected:
            verdict['labeled as expected' if label else 'unlabeled as expected'] += 1
            if not label:
                problems['unlabeled: ' + why].append(v)
        elif label is None:
            kind = 'MISSING, first seen without a creation' if first_seen else 'MISSING'
            verdict[kind] += 1
            problems[kind + ': ' + why].append(v)
        elif expected is None:
            verdict['EXTRA'] += 1
            problems['EXTRA (' + why + ')'].append(v)
        else:
            verdict['WRONG container'] += 1
            problems[f'WRONG: labeled {label}, expected {expected}'].append(v)
    print('  ' + ', '.join(f'{k}: {n}' for k, n in sorted(verdict.items())))
    for kind, vs in sorted(problems.items()):
        names = Counter(v.get('name') for v in vs)
        print(f'    {kind}: {len(vs)} versions, names {dict(names.most_common(6))}')
        if kind.startswith(('MISSING', 'EXTRA', 'WRONG')):
            for v in vs[:4]:
                print(f'        {v.get("name")} pid {v.get("pid")} ns {v.get("pid namespace")} {when(v)}')

    if samples:
        missing = total = 0
        for r in samples:
            if len(r['nspid']) == 1:
                continue   # runc's stages, in the host's PID namespace
            h = str(r['host_pid'])
            k = run_of(h, r['first_seen'])
            if not k or k[1] is None:
                continue
            total += 1
            cid = id_of_run.get(k)
            if not any(v.get('pid') == h and v.get('container_id') == cid for v in processes.values()):
                missing += 1
                if missing <= 4:
                    print(f'    sampled {h} {r["names"]} of {run_label(*k)}: no version labeled {cid}  <--')
        print(f'  sampled processes in a run: {total}, without a version labeled with their run: {missing}')

    print(f'\n== T4: network sockets ({s0})')
    label_of = {h: v.get('container_id') for h, v in snodes.items()}
    neighbours = defaultdict(set)
    for c, p, _ in sedges:
        neighbours[c].add(p)
        neighbours[p].add(c)
    per_container = Counter()
    reasons = Counter()
    bad = 0
    for h, v in snodes.items():
        if v.get('subtype') != 'network socket':
            continue
        ps = [snodes[o] for o in neighbours[h] if snodes.get(o, {}).get('type') == 'Process']
        labels = {p.get('container_id') for p in ps}
        net = v.get('net namespace')
        if v.get('container_id'):
            per_container[v['container_id']] += 1
            if labels != {v['container_id']} or any(p.get('net namespace') != net for p in ps):
                bad += 1
            continue
        if not any(p.get('container_id') for p in ps):
            continue   # not next to a labeled process
        if not net:
            reasons['no net namespace'] += 1
        elif None in labels:
            reasons['also used by an unlabeled process'] += 1
        elif len(labels) > 1:
            reasons['used by processes of several containers'] += 1
        elif any(p.get('net namespace') != net for p in ps):
            reasons['net namespace differs from a process\'s'] += 1
        else:
            reasons['?'] += 1
    print(f'  labeled sockets: {sum(per_container.values())} in {len(per_container)} containers; '
          f'labeled sockets breaking the rule: {bad}')
    for cid, n in per_container.most_common():
        print(f'    {cid}: {n}')
    print(f'  sockets next to a labeled process but left: {sum(reasons.values())} {dict(reasons)}')


if __name__ == '__main__':
    main()
