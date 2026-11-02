#!/usr/bin/env python3
"""Read auditd's af_unix plugin socket as fast as possible and save what arrives.

A second client next to SPADE's audit bridge: if it receives events the bridge
does not, the plugin drops events per client, i.e. when a client lags.
Usage (as root): af-unix-capture.py OUT [SOCKET]   Stops on SIGTERM/SIGINT.
"""
import signal
import socket
import sys

out_path = sys.argv[1]
sock_path = sys.argv[2] if len(sys.argv) > 2 else '/var/run/audispd_events'
stop = False


def handle(*_):
    global stop
    stop = True


signal.signal(signal.SIGTERM, handle)
signal.signal(signal.SIGINT, handle)
s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
s.connect(sock_path)
s.settimeout(0.5)
print(f'receive buffer: {s.getsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF)} bytes', file=sys.stderr)
total = 0
with open(out_path, 'wb') as out:
    while not stop:
        try:
            data = s.recv(1 << 20)
        except socket.timeout:
            continue
        if not data:
            break
        out.write(data)
        total += len(data)
print(f'captured {total} bytes', file=sys.stderr)
