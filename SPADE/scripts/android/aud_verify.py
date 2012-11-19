#!/usr/bin/python

# Licensed Under GPL v3
# Copyright (C) 2012 SRI International
# Author: Sharjeel Ahmed Qureshi

# Script used to verify the reliability of audit stream
# It checks for events missing their EOEs
# A small of missed EOE's can be safely ignored to cater for boundary events

## e.g. incoming line
## type=SYSCALL audit(1352750691.773:1719829): arch=40000028 syscall=3 per=840000 success=yes exit=1 a0=1e a1=4a62aa9c a2=10 a3=b2ef6aeb items=0 ppid=39 pid=171 auid=4294967295 uid=1000 gid=1000 euid=1000 suid=1000 fsuid=1000 egid=1000 sgid=1000 fsgid=1000 tty=(none) ses=4294967295 comm="er.ServerThread" exe="/system/bin/app_process" key=(null)
## 
## type=EOE audit(1352750691.773:1719829): 
##

import os
import sys
import re
import traceback
from collections import defaultdict


pattern_auditid = r'audit\([^:]+:([0-9]+)\):'
auditre = re.compile(pattern_auditid)

def usage():
    if len(sys.argv) < 2:
        print "Usage: %s <audit filename>" % sys.argv[0]
        sys.exit(1)

unfinished = dict()
unseens = []

first_aud_id = None
last_aud_id = None
syscall_count = 0

def process_line(line):
    global unfinished, unseens, first_aud_id, last_aud_id, syscall_count
    def key_val_split(x):
        if x.startswith("audit("):
            return ("msg",x)
        elif '=' in x:
            return x.split('=')
        else:
            return (i,'=')

    d = dict([ key_val_split(i) for i in line.split() ])

    if d.has_key('msg') and auditre.findall(d['msg']):
        auditid = int(auditre.findall(d['msg'])[0])
    else:
        print "PARSEERROR: AuditID not found in: " + line
        return

    if d['type'] == 'SYSCALL':
        if not first_aud_id:
            first_aud_id = auditid
        last_aud_id = auditid

        unfinished[auditid] = line
        syscall_count += 1
    elif d['type'] == 'EOE':
        if unfinished.get(auditid):
            del unfinished[auditid]
        else:
            unseens.append(auditid)
            # print "EOE event not seen before: " + str(auditid)

def process(f):
    for line in f:
        try:
            if line.startswith("type="):
                process_line(line)
        except Exception, e:
            print "Error in line: %s" % line
            traceback.print_exc()



def main():
    global unfinished
    # usage()
    f = open(sys.argv[1], 'r')
    process(f)
    print "--"
    print "Following event IDs did not get their EOEs: " + str(unfinished.keys())
    print "Total %d items did not get their EOEs" % len(unfinished)
    print "Total %d EOE received without their SYSCALL messages" % len(unseens)

    print "StartEventID: %d, EndEventID: %d, Diff: %d" % (first_aud_id, last_aud_id, last_aud_id - first_aud_id)
    print "SysCall Count: %d" % syscall_count
if __name__ == '__main__':
    main()
