#!/usr/bin/python

# Licensed Under GPL v3
# Copyright (C) 2012 SRI International
# Author: Sharjeel Ahmed Qureshi

# Script takes an audit log dump and generates summarized profiling data of the syscalls
# To generate the log dump, run Audit reporter with parameter "dump"

import os
import sys
import re
import traceback
from collections import defaultdict

SYSCALLS_MAP = { 2:"fork", 190:"vfork", 120:"clone", 11:"execve", 5:"open", 6:"close", 3:"read", 145:"readv", 180:"pread64", 4:"write", 146:"writev", 181:"pwrite64", 54:"ioctl", 9:"link", 83:"symlink", 10:"unlink", 14:"mknod", 38:"rename", 42:"pipe", 331:"pipe2", 359:"pipe2", 41:"dup", 63:"dup2", 203:"setreuid", 208:"setresuid", 213:"setuid", 92:"truncate", 93:"ftruncate", 15:"chmod", 94:"fchmod", 102:"socketcall", 290:"sendto", 296:"sendmsg", 292:"recvfrom", 297:"recvmsg", 283:"connect", 285:"accept", 281:"socket" }

# PID_MAP = { 1  :"/init", 2  :"kthreadd", 3  :"ksoftirqd/0", 4  :"events/0", 5  :"khelper", 6  :"suspend", 7  :"kblockd/0", 8  :"cqueue", 9  :"kseriod", 10 :"kmmcd", 11 :"pdflush", 12 :"pdflush", 13 :"kswapd0", 14 :"aio/0", 24 :"mtdblockd", 25 :"kstriped", 26 :"hid_compat", 27 :"rpciod/0", 28 :"mmcqd", 29 :"/sbin/ueventd", 30 :"/system/bin/auditd", 32 :"/system/bin/servicemanager", 33 :"/system/bin/vold", 35 :"/system/bin/netd", 36 :"/system/bin/debuggerd", 37 :"/system/bin/rild", 38 :"/system/bin/surfaceflinger", 39 :"zygote", 40 :"/system/bin/drmserver", 41 :"/system/bin/mediaserver", 42 :"/system/bin/dbus-daemon", 43 :"/system/bin/installd", 44 :"/system/bin/keystore", 45 :"/system/bin/qemud", 48 :"/system/bin/sh", 49 :"/sbin/adbd", 50 :"kauditd", 162 :"system_server", 240 :"com.android.inputmethod.latin", 258 :"com.android.phone", 270 :"com.android.launcher", 313 :"com.android.smspush", 324 :"com.android.settings", 341 :"android.process.acore", 445 :"com.android.systemui", 463 :"com.android.exchange", 476 :"android.process.media", 492 :"com.android.email", 517 :"com.android.voicedialer", 538 :"com.android.deskclock", 554 :"com.android.providers.calendar", 569 :"com.android.mms", 617 :"com.android.calendar", 741 :"com.android.calculator2", 765 :"/system/bin/sh", 1201 :"logcat", 1454 :"/system/bin/sh", 1456 :"dalvikvm", 1473 :"com.android.browser", 1516 :"/system/bin/sh", 1518 :"ps" }
pid_map = dict() # PID -> executable

is_pid_used = defaultdict(lambda: False)
is_syscall_used = defaultdict(lambda: False)
profiles = defaultdict(lambda: defaultdict(lambda: 0))

def process(f):
    global profiles
    global is_pid_used
    global is_syscall_used

    for line in f:
        try:
            d = dict( l.split('=') if '=' in l else (None, None) for l in line.split() ) 

            if d.get('type') == "SYSCALL":
                pid = int(d['pid'])
                syscall_no = int(d['syscall'])
                is_pid_used[pid] = True
                is_syscall_used[syscall_no] = True
                profiles[pid][syscall_no] += 1

                if not pid_map.get(pid) and (d.get('exe') or d.get('comm')):
                    exe = d.get('exe','').replace('"','')
                    comm = d.get('comm','').replace('"','')
                    pid_map[pid] = "%s(%s)" % (exe, comm)

        except Exception, e:
            sys.stderr.write("Exception: %s\nErroneous line: %s\n" % (e, line))

    # Print headers
    valid_syscalls = sorted([ syscall_no for syscall_no, v in is_syscall_used.iteritems() if v ])
    print "*," + ",".join( map(SYSCALLS_MAP.get, valid_syscalls) )

    # Print profile data
    for pid, row in profiles.iteritems():
        print str(pid_map.get(pid, pid)) + "," + ",".join( str(row[syscall]) for syscall in valid_syscalls )


def main():
    global unfinished
    # usage()
    f = open(sys.argv[1], 'r')
    process(f)

if __name__ == '__main__':
    main()
