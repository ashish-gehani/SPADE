#!/usr/bin/env python3
"""Compare the events auditd logged with the events SPADE's Audit reporter read.

auditd's af_unix plugin drops events its client cannot take without waiting,
and logs nothing (BUG_FIXES.md). So a trace is complete only if every event in
auditd's log, within the serial range both logs cover, also reached SPADE.
auditd also drops single records on their way to the plugin when its queue to
the plugins is full ("queue to plugins is full - dropping event"); a record
missing from an event leaves its serial number in place, so the records of each
event are compared too, by type. The last events SPADE read can lack records
because the bridge stopped reading in the middle of them: incomplete events at
the very end of SPADE's input are reported as cut off, not as lost.

Usage: compare-input.py AUDITD_LOG SPADE_INPUT_LOG
Prints a summary; exit status 1 if any event or record is missing from SPADE's input.
"""
import collections
import re
import sys

STAMP = re.compile(r'msg=audit\((\d+\.\d+):(\d+)\)')


TYPE = re.compile(r'^type=(\S+) ')


def events(path, records):
    """serial -> (time, syscall name or None, comm or None); counts (serial, type) in records"""
    out = {}
    with open(path, errors='replace') as f:
        for line in f:
            m = STAMP.search(line)
            if not m:
                continue
            serial = int(m.group(2))
            t = TYPE.match(line)
            records[(serial, t.group(1) if t else '?')] += 1
            if line.startswith('type=SYSCALL'):
                name = re.search(r' SYSCALL=(\w+)', line) or re.search(r' syscall=(\d+)', line)
                comm = re.search(r' comm="([^"]*)"', line)
                out[serial] = (float(m.group(1)), name.group(1) if name else '?', comm.group(1) if comm else '?')
            else:
                out.setdefault(serial, (float(m.group(1)), None, None))
    return out


def main():
    logged_records, read_records = collections.Counter(), collections.Counter()
    logged, read = events(sys.argv[1], logged_records), events(sys.argv[2], read_records)
    if not logged or not read:
        print(f'events in auditd log: {len(logged)}, in SPADE input: {len(read)}; nothing to compare')
        return 1
    lo, hi = max(min(logged), min(read)), min(max(logged), max(read))
    a = {s for s in logged if lo <= s <= hi}
    b = {s for s in read if lo <= s <= hi}
    missing = sorted(a - b)
    print(f'serials {lo}..{hi}: auditd logged {len(a)} events, SPADE read {len(b)}, '
          f'missing from SPADE: {len(missing)}, only in SPADE: {len(b - a)}')
    if missing:
        calls = [logged[s] for s in missing if logged[s][1]]
        print('  missing syscall events by syscall:', collections.Counter(c[1] for c in calls).most_common(6))
        print('  missing syscall events by process:', collections.Counter(c[2] for c in calls).most_common(6))
        print(f'  first missing: serial {missing[0]} at {logged[missing[0]][0]:.3f}')
    la = collections.Counter({k: v for k, v in logged_records.items() if k[0] in a and k[0] in b})
    lb = collections.Counter({k: v for k, v in read_records.items() if k[0] in a and k[0] in b})
    gone, extra = la - lb, lb - la
    # incomplete events at the very end of SPADE's input: the bridge stopped in the middle of them
    incomplete = {serial for serial, _ in gone}
    cut = set()
    for serial in sorted(b, reverse=True):
        if serial not in incomplete:
            break
        cut.add(serial)
    if cut:
        n_cut = sum(n for (serial, _), n in gone.items() if serial in cut)
        print(f'  cut off at the end: {n_cut} record(s) of the last {len(cut)} event(s) SPADE read, '
              f'serials {min(cut)}..{max(cut)} (the bridge stopped in the middle of them; not counted)')
        gone = collections.Counter({k: v for k, v in gone.items() if k[0] not in cut})
    print(f'records of those events: auditd logged {sum(la.values())}, SPADE read {sum(lb.values())}, '
          f'missing from SPADE: {sum(gone.values())}, only in SPADE: {sum(extra.values())}')
    for (serial, kind), n in sorted(gone.items())[:10]:
        ev = logged[serial]
        print(f'  missing: {n} {kind} record(s) of serial {serial} at {ev[0]:.3f} '
              f'(syscall {ev[1] or "-"}, comm {ev[2] or "-"}; SPADE has {sorted(k for (s, k) in lb if s == serial)})')
    return 1 if missing or gone else 0


if __name__ == '__main__':
    sys.exit(main())
