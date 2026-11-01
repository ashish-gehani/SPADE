#!/usr/bin/env python3
"""Compare the audit module's namespace records with /proc ground truth.

For every namespace record (ns_NEWPROCESS, ns_SETNS, ns_UNSHARE) in a run,
decide what ns_ns_pid and ns_inum_pid should have been. The subject is the new
child for NEWPROCESS and the caller for SETNS and UNSHARE.

  observed    the sampler caught the subject: the expected ns pid is the last
              NSpid entry and the expected PID namespace is its inode.
  structural  not sampled. In the host PID namespace the ns pid must equal the
              host pid; in any other namespace it must differ from it (equal
              means the record holds the creator's view, the bug 9fd54e0c fixes).
  unverified  neither applies.

It also checks that every unshare/nsenter process the scenarios started got
its UNSHARE/SETNS record, and how many processes born in each scenario got a
NEWPROCESS record at all (creation-hook coverage).

Usage: ns-ab-analyze.py BASE_DIR FIXED_DIR > report.md
"""
import json
import re
import sys
from collections import Counter, defaultdict

HOST_PID_NS = 4026531836   # PROC_PID_INIT_INO
SCENARIO = re.compile(r'^S\d+:')


def load(run_dir):
    marks, recs = [], []
    with open(f'{run_dir}/audit-slice.log', errors='replace') as f:
        for line in f:
            m = re.search(r'msg=audit\((\d+\.\d+):(\d+)\)', line)
            if not m:
                continue
            t = float(m.group(1))
            # auditctl -m logs the text as msg='text=nsab:...'
            mark = re.search(r"msg='(?:text=)?nsab:([^ ']+)", line)
            if mark:
                marks.append((t, mark.group(1)))
                continue
            if 'ns_subtype=ns_namespaces' not in line:
                continue
            kv = dict(re.findall(r'\b(ns_[a-z_]+)=(\S+)', line))
            recs.append({
                't': t,
                'op': kv.get('ns_operation', 'ns_?')[3:],
                'ns_pid': int(kv.get('ns_ns_pid', -1)),
                'host_pid': int(kv.get('ns_host_pid', -1)),
                'pid_ns': int(kv.get('ns_inum_pid', -1)),
                'zero_ns': sorted(k for k, v in kv.items() if k.startswith('ns_inum_') and v == '0'),
            })
    with open(f'{run_dir}/sampler.jsonl') as f:
        procs = [json.loads(line) for line in f]
    lost = re.findall(r'lost (\d+)', open(f'{run_dir}/run.log').read())
    return marks, recs, procs, lost


def windows(marks):
    begins, out = {}, []
    for t, label in marks:
        name, _, edge = label.rpartition(':')
        if not SCENARIO.match(label):
            continue
        if edge == 'begin':
            begins[name] = t
        elif edge == 'end' and name in begins:
            out.append((name, begins.pop(name), t))
    return out


def subject(rec, by_pid):
    """The sampled process a record is about, or None."""
    cands = by_pid.get(rec['host_pid'], [])
    t = rec['t']
    if rec['op'] == 'NEWPROCESS':      # the new child: first seen just after creation
        cands = [p for p in cands if t - 0.2 <= p['first_seen'] <= t + 1.5]
    else:                               # the caller: alive when it called (the sampler can lag
                                        # behind a busy system, so allow a late first sighting)
        cands = [p for p in cands if p['first_seen'] <= t + 1.5 and p['last_seen'] >= t - 0.2]
    return min(cands, key=lambda p: abs(p['first_seen'] - t), default=None)


def judge(rec, subj):
    """(verdict, basis, expected ns pid, reason)"""
    if rec['zero_ns']:
        return 'WRONG', 'record', None, 'zero inode in ' + ','.join(rec['zero_ns'])
    if subj and subj['nspid']:
        exp_pid, exp_ns = subj['nspid'][-1], subj['pid_ns']
        why = []
        if rec['ns_pid'] != exp_pid:
            why.append(f"ns_pid {rec['ns_pid']}, expected {exp_pid}")
        if rec['pid_ns'] != exp_ns:
            why.append(f"pid ns {rec['pid_ns']}, expected {exp_ns}")
        return ('WRONG' if why else 'OK'), 'observed', exp_pid, '; '.join(why)
    if rec['pid_ns'] == HOST_PID_NS:
        if rec['ns_pid'] == rec['host_pid']:
            return 'OK', 'structural', rec['host_pid'], ''
        return 'WRONG', 'structural', rec['host_pid'], 'host namespace but ns_pid != host pid'
    if rec['ns_pid'] == rec['host_pid']:
        return 'WRONG', 'structural', None, "non-host namespace but ns_pid == host pid (creator's view)"
    return 'UNVERIFIED', '-', None, ''


def names(p):
    return '/'.join(dict.fromkeys(n for n in p['names'] if n)) if p else '?'


def analyze(run_dir):
    marks, recs, procs, lost = load(run_dir)
    wins = windows(marks)
    by_pid = defaultdict(list)
    for p in procs:
        by_pid[p['host_pid']].append(p)

    rows = []
    for r in recs:
        scen = next((w[0] for w in wins if w[1] <= r['t'] <= w[2]), '(between)')
        subj = subject(r, by_pid)
        verdict, basis, exp, why = judge(r, subj)
        rows.append({**r, 'scenario': scen, 'verdict': verdict, 'basis': basis,
                     'expected': exp, 'why': why, 'name': names(subj)})

    calls = []      # did each unshare/nsenter process get its record?
    coverage = {}   # NEWPROCESS records for processes born in each window
    for scen, b, e in wins:
        for p in procs:
            if not (b <= p['first_seen'] <= e):
                continue
            want = 'UNSHARE' if 'unshare' in p['names'] else 'SETNS' if 'nsenter' in p['names'] else None
            if want:
                got = [r for r in rows if r['op'] == want and r['host_pid'] == p['host_pid'] and b <= r['t'] <= e]
                calls.append((scen, p, want, got))
        born = [p for p in procs if b <= p['first_seen'] <= e and p['ppid'] != 2]
        created = {r['host_pid'] for r in rows if r['op'] == 'NEWPROCESS' and b - 0.2 <= r['t'] <= e + 0.2}
        missing = [p for p in born if p['host_pid'] not in created]
        coverage[scen] = (len(born), len(born) - len(missing), missing)
    return wins, rows, calls, coverage, lost


def interesting(r):
    return r['op'] != 'NEWPROCESS' or r['pid_ns'] != HOST_PID_NS or r['verdict'] == 'WRONG'


def report_variant(label, wins, rows, calls, coverage, lost):
    print(f'## {label}\n')
    print(f'Audit `lost` counter before/after: {" -> ".join(lost) or "?"}\n')
    print('| scenario | records | OK | WRONG | unverified |')
    print('|---|---|---|---|---|')
    for scen in [w[0] for w in wins] + ['(between)']:
        c = Counter(r['verdict'] for r in rows if r['scenario'] == scen)
        if c:
            print(f"| {scen} | {sum(c.values())} | {c['OK']} | {c['WRONG']} | {c['UNVERIFIED']} |")
    print('\nRecords outside the host PID namespace, SETNS/UNSHARE records, and every WRONG:\n')
    print('| scenario | op | process | host pid | ns_ns_pid | expected | ns_inum_pid | verdict | basis | why |')
    print('|---|---|---|---|---|---|---|---|---|---|')
    for r in rows:
        if interesting(r):
            ns = 'host' if r['pid_ns'] == HOST_PID_NS else r['pid_ns']
            exp = '' if r['expected'] is None else r['expected']
            print(f"| {r['scenario']} | {r['op']} | {r['name']} | {r['host_pid']} | {r['ns_pid']} | {exp} "
                  f"| {ns} | {r['verdict']} | {r['basis']} | {r['why']} |")
    print('\nunshare/nsenter processes and their records:\n')
    print('| scenario | process | host pid | NSpid | expected record | found |')
    print('|---|---|---|---|---|---|')
    for scen, p, want, got in calls:
        print(f"| {scen} | {names(p)} | {p['host_pid']} | {p['nspid']} | {want} | {len(got)} |")
    print('\nCreation coverage (processes born in the window with a NEWPROCESS record):\n')
    print('| scenario | born | recorded | not recorded |')
    print('|---|---|---|---|')
    for scen, (born, ok, missing) in coverage.items():
        miss = ', '.join(sorted(Counter(names(p) for p in missing).elements())) or '-'
        print(f'| {scen} | {born} | {ok} | {miss} |')
    print()


def key_rows(rows):
    """Records worth pairing across variants, keyed by (scenario, op, process, ordinal).

    Records whose subject the sampler never saw are left out unless WRONG: they
    are mostly setns calls from Go runtime threads in dockerd and containerd,
    reported by thread id, and they cannot be paired across runs by name.
    """
    seen, out = Counter(), {}
    for r in rows:
        if interesting(r) and r['scenario'] != '(between)' and (r['name'] != '?' or r['verdict'] == 'WRONG'):
            k = (r['scenario'], r['op'], r['name'])
            seen[k] += 1
            out[k + (seen[k],)] = r
    return out


def main():
    base, fixed = (analyze(d) for d in sys.argv[1:3])
    print('# ns pid A/B: base vs fixed audit kernel module\n')
    report_variant('base (without 9fd54e0c, d445c7cc)', *base)
    report_variant('fixed (with 9fd54e0c, d445c7cc)', *fixed)

    print('## Side by side\n')
    print('Paired by scenario, operation, process name and order of appearance.\n')
    print('| scenario | op | process | base ns_ns_pid | base verdict | fixed ns_ns_pid | fixed verdict | expected |')
    print('|---|---|---|---|---|---|---|---|')
    kb, kf = key_rows(base[1]), key_rows(fixed[1])
    for k in sorted(set(kb) | set(kf)):
        b, f = kb.get(k), kf.get(k)
        exp = next((x['expected'] for x in (f, b) if x and x['expected'] is not None), '')
        print(f"| {k[0]} | {k[1]} | {k[2]} #{k[3]} "
              f"| {b['ns_pid'] if b else 'no record'} | {b['verdict'] if b else '-'} "
              f"| {f['ns_pid'] if f else 'no record'} | {f['verdict'] if f else '-'} | {exp} |")


if __name__ == '__main__':
    main()
