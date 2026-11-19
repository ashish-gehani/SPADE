#!/usr/bin/env python3
"""Explain the Audit reporter's "No FD with number" closes in a replay.

For each such close, the descriptor's timeline in auditd's log for the same
process: recorded creations (syscalls that return a descriptor, FD_PAIR
records of pipe and socketpair, the kernel module's accepts, whose pid is a
thread id and is mapped to its process), and recorded closes. A close of an
unknown descriptor is a gap in SPADE only if the last recorded event on it
before the close is a creation; otherwise the descriptor came from something
SPADE does not record (or from before tracing). Events are ordered by their
serial numbers: two events in the same millisecond are common.

Usage: nofd.py SPADE_LOG AUDITD_SLICE
"""
import bisect
import collections
import re
import sys

log, slice_path = sys.argv[1], sys.argv[2]
NOFD = re.compile(r"\[Time:EventID=([0-9.]+):(\d+), SYSCALL=(\w+)\] No FD with number '(\d+)' for pid '(\d+)'")
NODIR = re.compile(r"\[Time:EventID=([0-9.]+):(\d+), SYSCALL=(\w+)\] Missing directory FD for pid '(\d+)' with FD '(\d+)'")
cases = []      # closes of unknown descriptors: (serial, fd, pid)
dir_cases = []  # *at calls on an unknown directory descriptor: (serial, fd, pid)
said = {}       # serial -> what the reporter logged about that event first
ANY = re.compile(r"\[Time:EventID=[0-9.]+:(\d+), SYSCALL=\w+\] (\D+)")
for line in open(log, errors='replace'):
    m = ANY.search(line)
    if m:
        said.setdefault(int(m.group(1)), m.group(2).strip(" '"))
    m = NOFD.search(line)
    if m:
        cases.append((int(m.group(2)), m.group(4), m.group(5)))
    m = NODIR.search(line)
    if m:
        dir_cases.append((int(m.group(2)), m.group(5), m.group(4)))
pids = {c[2] for c in cases + dir_cases}

# syscalls whose exit value is a new descriptor (x86_64)
CREATES = {2: 'open', 257: 'openat', 85: 'creat', 437: 'openat2', 41: 'socket', 43: 'accept', 288: 'accept4',
           32: 'dup', 33: 'dup2', 292: 'dup3', 319: 'memfd_create', 290: 'eventfd2', 291: 'epoll_create1',
           283: 'timerfd_create', 289: 'signalfd4', 294: 'inotify_init1', 434: 'pidfd_open', 304: 'open_by_handle_at'}
SYSCALL = re.compile(r'^type=SYSCALL msg=audit\(([0-9.]+):(\d+)\): arch=\S+ syscall=(\d+) success=(\w+) exit=(-?\d+) a0=(\w+) a1=(\w+) .* pid=(\d+) ')
FDPAIR = re.compile(r'^type=FD_PAIR msg=audit\(([0-9.]+):(\d+)\): fd0=(\d+) fd1=(\d+)')
NETIO = re.compile(r'msg=audit\([0-9.]+:(\d+)\): netio_intercepted="syscall=(\d+) exit=(-?\d+) success=1 fd=\S+ pid=(\d+) ')

events = collections.defaultdict(list)   # (pid, fd) -> [(serial, 'create:<name>' | 'close')]
serial_pid = {}                          # serial -> pid, for FD_PAIR
tgid_of = {}                             # thread id -> process id, from clone records
netio = []
close_ranges = collections.defaultdict(list)   # pid -> [(serial, first, last)], those that close
with open(slice_path, errors='replace') as f:
    for line in f:
        if line.startswith('type=SYSCALL'):
            m = SYSCALL.match(line)
            if not m:
                continue
            _, serial, sc, ok, ex, a0, a1, pid = m.groups()
            t, sc, ex = int(serial), int(sc), int(ex)
            if sc in (56, 435) and ok == 'yes' and ex > 0:
                tgid_of[str(ex)] = pid
            if pid not in pids or ok != 'yes':
                continue
            if sc in CREATES and ex >= 0:
                events[(pid, str(ex))].append((t, 'create:' + CREATES[sc]))
            elif sc == 72 and int(a1, 16) in (0, 1030) and ex >= 0:   # fcntl F_DUPFD(_CLOEXEC)
                events[(pid, str(ex))].append((t, 'create:fcntl'))
            elif sc == 3:
                events[(pid, str(int(a0, 16)))].append((t, 'close'))
            elif sc == 436:   # close_range(first, last, flags); CLOSE_RANGE_CLOEXEC (4) only marks
                a2 = re.search(r' a2=(\w+) ', line)
                if not (a2 and int(a2.group(1), 16) & 4):
                    close_ranges[pid].append((t, int(a0, 16), int(a1, 16)))
            elif sc in (22, 293, 53):
                serial_pid[serial] = pid
        elif line.startswith('type=FD_PAIR'):
            m = FDPAIR.match(line)
            if m and m.group(2) in serial_pid:
                pid = serial_pid[m.group(2)]
                for fd in (m.group(3), m.group(4)):
                    events[(pid, fd)].append((int(m.group(2)), 'create:pipe/socketpair'))
        elif 'netio_intercepted=' in line:
            m = NETIO.search(line)
            if m and int(m.group(2)) in (43, 288) and int(m.group(3)) >= 0:
                netio.append((int(m.group(1)), m.group(4), m.group(3)))
for t, tid, fd in netio:
    pid = tgid_of.get(tid, tid)
    if pid in pids:
        events[(pid, fd)].append((t, 'create:accept (module)'))
for k in events:
    events[k].sort()



def judge(what, rows):
    verdict = collections.Counter()
    gaps = collections.Counter()
    at_creation = collections.Counter()   # what the reporter logged about the creating event
    examples = []
    for t, fd, pid in rows:
        ev = events.get((pid, fd), [])
        i = bisect.bisect_left(ev, (t, ''))   # events before this one
        last = ev[i - 1][1] if i > 0 else None
        last_t = ev[i - 1][0] if i > 0 else None
        # a close_range covering the descriptor after its last recorded event closed it
        n = int(fd)
        if last and last.startswith('create:') and any(last_t <= ct < t and lo <= n <= hi for ct, lo, hi in close_ranges.get(pid, [])):
            last = 'close'
        if last and last.startswith('create:'):
            verdict['gap: last recorded event was a creation'] += 1
            gaps[last] += 1
            at_creation[said.get(last_t, '(nothing)')] += 1
            if len(examples) < 3:
                examples.append((pid, fd, t, ev[max(0, i - 3):i]))
        elif last == 'close':
            verdict['created unrecorded: last recorded event was a close'] += 1
        else:
            verdict['created unrecorded: no recorded event on it before'] += 1
    print(f'{len(rows)} {what}:')
    for k, v in verdict.most_common():
        print(f'  {v:6d}  {k}')
    print('  gaps by the creating syscall:', gaps.most_common(8))
    print('  what the reporter logged about the creating event:', at_creation.most_common(6))
    for e in examples:
        print('    e.g. pid', e[0], 'fd', e[1], 'in event', e[2], 'last events', e[3])


judge('closes of unknown descriptors', cases)
judge('calls relative to an unknown directory descriptor', dir_cases)
