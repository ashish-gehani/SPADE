#!/usr/bin/python
import sys
import re

f = open(sys.argv[1])

line = f.readline()

read_threads = {}
write_threads = {}

count = 1

while line:
#supress multiple reads and writes from same thread
    if (re.search("\"type\":\"EVENT_READ\"", line)):
        p = re.search('(?<=\"threadId\":)\w+', line)
        tid = p.group(0)
        if (read_threads.get(tid)):
            line = f.readline()
            continue
        read_threads[tid] = "added"
    elif (re.search("\"type\":\"EVENT_WRITE\"", line)):
        p = re.search('(?<=\"threadId\":)\w+', line)
        tid = p.group(0)
        if (write_threads.get(tid)):
            line = f.readline()
            continue
        write_threads[tid] = "added"

    line = re.sub(r'\"sequence":[0-9\,]+', r'', line)
    line = re.sub(r'\"threadId":[0-9\,]+', r'', line)
    line = re.sub(r'\"start\ time\":\"[\-0-9\.]+\"', r'', line)
    line = re.sub(r'\"end\ time\":\"[\-0-9\.]+\"', r'', line)
    line = re.sub(r'\"uuid\":\"[0-9a-f]+\"\,?', r'', line)
    line = re.sub(r'\"startTimestampNanos\":[0-9\.\,]+', r'', line)
    line = re.sub(r'\"timestampNanos\":[0-9\.\,]+', r'', line)
    line = re.sub(r'\"com.bbn.tc.schema.avro.UUID\":\"[0-9a-f]+\"', r'', line)
    line = re.sub(r'\"subject\":\"[0-9a-f]+\"\,?', r'', line)
    line = re.sub(r'\"localPrincipal\":\"[0-9a-f]+\"\,?', r'', line)
    line = re.sub(r'\"pid\":\"[0-9]+\"\,?', r'', line)
    line = re.sub(r'\"long\":\"[0-9]+\"\,?', r'', line)
    line = re.sub(r'\"long\":[0-9\,]+', r'', line)
    line = re.sub(r'\"ppid\":\"[0-9]+\"\,?', r'', line)
    line = re.sub(r'\"tgid\":\"[0-9]+\"\,?', r'', line)
    line = re.sub(r'\/proc\/[0-9]+\/', r'\/proc\/', line)
    line = re.sub(r'\"cid\":[0-9\,]+', r'', line)
    line = re.sub(r'\"cwd\":\"[0-9a-z\/]+\",?', r'', line)
    line = re.sub(r'\"mode\":\"[0-9]+\",?', r'', line)
    line = re.sub(r'\"memoryAddress\":[0-9\,]+', r'', line)
    line = re.sub(r'\"localPort\":[0-9\,]+', r'', line)
    line = re.sub(r'\"seen time\":\"[0-9\.]+\"', r'', line)
    line = re.sub(r'\"remotePort\":[0-9\,]+', r'', line)
    line = re.sub(r'\"[\/a-z0-9]+unit\_tests\/tests', r'unit\_tests\/tests', line)
    line = re.sub(r'\"EVENT_WRITE\":\"[0-9]+\"', r'\"EVENT_WRITE\"', line)
    line = re.sub(r'\"EVENT_READ\":\"[0-9]+\"', r'\"EVENT_READ\"', line)
    print line
    line = f.readline()
     
f.close()
