#!/usr/bin/python

# Licensed Under GPL v3
# Copyright (C) 2012 SRI International
# Author: Sharjeel Ahmed Qureshi

# Script used to verify the reliability of audit stream
# It checks for events missing their EOEs
# A handful of missed EOE's can be safely ignored

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


def process_line(line):
    global unfinished, unseens
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
        unfinished[auditid] = line
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
if __name__ == '__main__':
    main()
