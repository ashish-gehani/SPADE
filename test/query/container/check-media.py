#!/usr/bin/env python3
"""Check trace 3 (M, the media microservices) against its expectations.

Ground truth:
  auditd-slice.log  the kernel module's NEWPROCESS records, as auditd logged
                    them (Part A checked those records against the kernel):
                    every process created in the trace, with its PID namespace.
                    A container run is a process with ns pid 1 in a namespace
                    other than the host's; runs of a reused ID are numbered by
                    time, as getContainerBoundary('X', n) numbers them.
  sampler.jsonl     every process the cgroup sampler saw in each container
                    (each container's cgroup, every 0.1 s).
  truth.txt         each container's last run from `docker inspect`, and how
                    often Docker restarted it.
Results: <storage>/client.log (and client-extra.log) and *.dot from query.sh.

  M1  getContainerInit: one start per run, whose init is the run's init
  M2  every sampled process of a run is a member of that run's boundary; no
      boundary holds another run's processes; its only host neighbour is the
      runc process that created the run's init (the sampler also sees runc's
      earlier stages in the cgroup, which are further up and not expected)
  M3  network sockets in each boundary
  M4  the same results on every storage

Usage: check-media.py [TRACE_DIR]
"""
import json
import os
import re
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dotsum import read  # noqa: E402

T = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser('~/stage-b/trace3')
STORAGES = [s for s in ('PostgreSQL', 'Neo4j', 'Quickstep') if os.path.isdir(os.path.join(T, s))]
HOST = '4026531836'
CREATE = ('fork', 'clone', 'vfork', 'clone3')
NEWPROC = re.compile(r'msg=audit\((\d+\.\d+):\d+\): .*ns_operation=ns_NEWPROCESS ns_ns_pid=(\d+) '
                     r'ns_host_pid=(\d+) .*ns_inum_pid=(\d+) ')


def kernel_truth():
    """host pid -> [(time, ns pid, X)], from the kernel module's records"""
    procs = defaultdict(list)
    with open(os.path.join(T, 'auditd-slice.log'), errors='replace') as f:
        for line in f:
            if 'ns_NEWPROCESS' not in line:
                continue
            m = NEWPROC.search(line)
            if m:
                procs[m.group(3)].append((float(m.group(1)), m.group(2), m.group(4)))
    return procs


def docker_truth():
    last = {}
    for line in open(os.path.join(T, 'truth.txt')):
        if line.startswith('M container='):
            kv = dict(re.findall(r'(\w+)=(\S+)', line))
            last[kv['id']] = kv
    return last


def sampled():
    out = []
    for line in open(os.path.join(T, 'sampler.jsonl')):
        out.append(json.loads(line))
    return out


def queries(storage):
    """result name -> (call, error or None), from the client logs"""
    out = {}
    for log_name in ('client.log', 'client-extra.log'):
        path = os.path.join(T, storage, log_name)
        if not os.path.exists(path):
            continue
        log = open(path, errors='replace').read().split('\n')
        for i, line in enumerate(log):
            m = re.match(r'^-> \$(\w+) = \$base\.(.*)$', line)
            if m:
                err = None
                for nxt in log[i + 1:i + 4]:
                    if nxt.startswith('Error'):
                        err = nxt[:600]
                        break
                    if nxt.startswith('-> '):
                        break
                out[m.group(1)] = (m.group(2), err)
    return out


def graph(storage, name):
    path = os.path.join(T, storage, name + '.dot')
    return read(path) if os.path.exists(path) else ({}, [])


def main():
    procs = kernel_truth()
    last = docker_truth()
    samples = sampled()
    name_of = {cid: kv['container'].replace('mediamicroservices-', '') for cid, kv in last.items()}

    # runs: the kernel's container inits, numbered by time within each namespace
    inits = sorted((t, h, x) for h, recs in procs.items() for (t, nspid, x) in recs
                   if nspid == '1' and x != HOST)
    runs_of_x = defaultdict(list)
    for t, h, x in inits:
        runs_of_x[x].append((t, h))
    container_of_pid = {str(r['host_pid']): r['container'] for r in samples}
    for cid, kv in last.items():
        container_of_pid.setdefault(kv['init_host_pid'], cid)

    run_no = {}   # init host pid -> (its container's run number, the container's runs)
    by_container = defaultdict(list)
    for t, h, x in inits:
        by_container[container_of_pid.get(h)].append(h)
    for cid, hs in by_container.items():
        for i, h in enumerate(hs, 1):
            run_no[h] = (i, len(hs))

    def run_name(x, n):
        return f'id_{x}_{n}' if len(runs_of_x[x]) > 1 else f'id_{x}'

    def run_of(h, created):
        """(X, n) of the run a process belongs to, from its NEWPROCESS record"""
        recs = [r for r in procs.get(h, []) if r[0] <= created + 1.0]
        if not recs:
            return None
        x = recs[-1][2]
        if x == HOST or x not in runs_of_x:
            return (x, None)
        n = 0
        for i, (t, _) in enumerate(runs_of_x[x], 1):
            if t <= recs[-1][0]:
                n = i
        return (x, n or 1)

    print(f'trace: {T}\nstorages: {", ".join(STORAGES)}')
    print(f'containers: {len(last)}, Docker restarts: {sum(int(kv["restarts"]) for kv in last.values())}, '
          f'runs (kernel inits): {len(inits)} in {len(runs_of_x)} namespaces, reused: '
          f'{ {x: len(r) for x, r in runs_of_x.items() if len(r) > 1} }, processes created: {len(procs)}, '
          f'sampled: {len(samples)}')
    s0 = STORAGES[0]
    q = {s: queries(s) for s in STORAGES}

    print('\n== M4: same results on every storage')
    names = sorted(set.intersection(*(set(f[:-4] for f in os.listdir(os.path.join(T, s)) if f.endswith('.dot'))
                                      for s in STORAGES)))
    differ = 0
    for n in names:
        sig = {}
        for s in STORAGES:
            nodes, edges = graph(s, n)
            sig[s] = (frozenset(nodes), len(edges))
        if len(set(sig.values())) != 1:
            differ += 1
            print(f'  {n} DIFFERS: ' + ', '.join(f'{s}={len(v[0])}/{v[1]}' for s, v in sig.items()))
    errs = {s: {k: re.sub(r'spade_graph_\d+|graph_\d+', 'G', v[1]) for k, v in q[s].items() if v[1]}
            for s in STORAGES}
    if len({frozenset(e.items()) for e in errs.values()}) != 1:
        differ += 1
        print('  error messages differ between storages:', {s: sorted(e) for s, e in errs.items()})
    print(f'  {len(names)} graphs and {len(errs[s0])} errors compared, {differ} differences')
    for k, v in sorted(errs[s0].items()):
        print(f'  {k} ({q[s0][k][0]}): {v[:300]}')

    print(f'\n== M1: getContainerInit ({s0})')
    nodes, edges = graph(s0, 'init')
    starts = set()
    for c, p, e in edges:
        child = nodes.get(c, {})
        if e.get('operation') in CREATE and child.get('ns pid') == '1' \
                and child.get('pid namespace') not in (HOST, '-1', None):
            starts.add((child.get('pid'), child.get('pid namespace')))
    want = {(h, x) for _, h, x in inits}
    print(f'  starts: {len(starts)}, kernel inits: {len(want)}, both: {len(starts & want)}')
    for h, x in sorted(want - starts):
        print(f'    no start for the init {h} in {x} ({name_of.get(container_of_pid.get(h), "?")})')
    for h, x in sorted(starts - want):
        print(f'    a start the kernel does not have: {h} in {x}')

    print(f'\n== M2, M3: boundaries ({s0})')
    all_x = set(runs_of_x)
    expected = defaultdict(set)   # (X, n) -> sampled member pids
    unknown = []
    for r in samples:
        h = str(r['host_pid'])
        if len(r['nspid']) == 1:
            continue   # runc's stages, in the host's PID namespace
        run = run_of(h, r['first_seen'])
        if run is None or run[1] is None:
            unknown.append((h, r['names'], r['nspid']))
            continue
        expected[run].add(h)
    if unknown:
        print(f'  sampled processes in a container namespace without a NEWPROCESS record: {len(unknown)}, '
              f'e.g. {unknown[:3]}')
    total_missing = total = bad_neighbours = 0
    for x in sorted(runs_of_x, key=lambda x: runs_of_x[x][0][0]):
        for n, (t, init) in enumerate(runs_of_x[x], 1):
            name = run_name(x, n)
            cid = container_of_pid.get(init)
            r, rs = run_no.get(init, (0, 0))
            label = f'{name_of.get(cid, "?")} run {r} of {rs}'
            if name not in q[s0]:
                print(f'  {label:44s} {name}: not queried')
                continue
            if q[s0][name][1]:
                print(f'  {label:44s} {name}: ERROR {q[s0][name][1][:200]}')
                continue
            nodes, edges = graph(s0, name)
            members, foreign, hostn, unlabelled, socks = set(), Counter(), Counter(), Counter(), 0
            host_pids, creator = set(), set()
            # the process whose clone made the init (runc uses CLONE_PARENT, so the
            # init's ppid is the creator's parent, not the creator)
            for c, p, e in edges:
                child = nodes.get(c, {})
                if e.get('operation') in CREATE and child.get('pid') == init \
                        and child.get('pid namespace') == x and p in nodes:
                    creator.add(nodes[p].get('pid'))
            for v in nodes.values():
                if v.get('type') == 'Process':
                    ns = v.get('pid namespace')
                    if ns == x:
                        members.add(v.get('pid'))
                    elif ns == HOST:
                        hostn[v.get('name')] += 1
                        host_pids.add(v.get('pid'))
                    elif ns == '-1':
                        unlabelled[v.get('pid')] += 1
                    elif ns in all_x:
                        foreign[ns] += 1
                elif v.get('subtype') == 'network socket':
                    socks += 1
            exp = expected.get((x, n), set())
            missing = sorted(exp - members, key=int)
            total_missing += len(missing)
            total += len(exp)
            own = sum(c for p, c in unlabelled.items() if p in members)
            only_creator = host_pids == creator and len(creator) == 1
            print(f'  {label:44s} {name}: sampled {len(exp)}, missing {len(missing)}, members {len(members)}, '
                  f'other containers {dict(foreign) or "-"}, sockets {socks}, host neighbours {dict(hostn)} '
                  f'({"the init creator" if only_creator else "NOT only the init creator"}), '
                  f'unlabelled versions {sum(unlabelled.values())} ({own} with members\' pids)'
                  f'{"  <--" if missing or foreign or not only_creator else ""}')
            bad_neighbours += 0 if only_creator else 1
            for h in missing[:4]:
                rec = next(r for r in samples if str(r['host_pid']) == h)
                print(f'      missing pid {h} {rec["names"]} NSpid {rec["nspid"]} ppid {rec["ppid"]}')
    print(f'  sampled processes missing from their run\'s boundary: {total_missing} of {total}; '
          f'boundaries with a host neighbour other than the init\'s creator: {bad_neighbours}')


if __name__ == '__main__':
    main()
