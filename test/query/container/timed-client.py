#!/usr/bin/env python3
"""Run SPADE's query client on a file of commands, one at a time, and time each.

Usage: timed-client.py SPADE_HOME QUERIES CLIENT_LOG TIMES [TIMEOUT]

Starts `bin/spade query`, sends the lines of QUERIES one by one, and after each
waits until the client prompts again ("-> " at the end of its output), so a
command's time is from sending it to the next prompt: the query, plus printing
or exporting its result. The client's whole output goes to CLIENT_LOG, and
"<seconds>\t<command>" for each command to TIMES. A command that takes longer
than TIMEOUT seconds (3600 by default) ends the run.
"""
import os
import subprocess
import sys
import threading
import time

PROMPT = b'-> '


def main():
    spade, queries, log_path, times_path = sys.argv[1:5]
    limit = float(sys.argv[5]) if len(sys.argv) > 5 else 3600.0
    proc = subprocess.Popen([os.path.join(spade, 'bin', 'spade'), 'query'], cwd=spade,
                            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    state = {'out': bytearray(), 'eof': False}
    cond = threading.Condition()

    def reader():
        with open(log_path, 'wb') as log:
            while True:
                chunk = proc.stdout.read1(65536)
                if not chunk:
                    break
                log.write(chunk)
                log.flush()
                with cond:
                    state['out'] += chunk
                    cond.notify_all()
        with cond:
            state['eof'] = True
            cond.notify_all()

    threading.Thread(target=reader, daemon=True).start()

    def wait_prompt(since):
        deadline = time.monotonic() + limit
        with cond:
            while True:
                if state['out'][since:].endswith(PROMPT):
                    return True
                if state['eof'] or time.monotonic() > deadline:
                    return False
                cond.wait(1.0)

    if not wait_prompt(0):
        sys.exit('no prompt from the query client')
    with open(times_path, 'w') as times:
        for line in open(queries):
            cmd = line.rstrip('\n')
            if not cmd:
                continue
            with cond:
                since = len(state['out'])
            start = time.monotonic()
            proc.stdin.write(cmd.encode() + b'\n')
            proc.stdin.flush()
            if cmd == 'exit':
                break
            ok = wait_prompt(since)
            times.write(f'{time.monotonic() - start:.3f}\t{cmd}\n')
            times.flush()
            if not ok:
                times.write(f'-\tstopped: no prompt after {limit:.0f} s or the client ended\n')
                proc.kill()
                sys.exit(1)
    try:
        proc.wait(60)
    except subprocess.TimeoutExpired:
        proc.kill()


if __name__ == '__main__':
    main()
