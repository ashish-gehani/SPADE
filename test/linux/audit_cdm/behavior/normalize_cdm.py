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
#        p = re.search('(?<=\"threadId\":)\w+', line)
        p = re.search('(?<=\"threadId\":\{\"int\":)\w+', line)
        tid = p.group(0)
        if (read_threads.get(tid)):
            line = f.readline()
            continue
        read_threads[tid] = "added"
    elif (re.search("\"type\":\"EVENT_WRITE\"", line)):
        p = re.search('(?<=\"threadId\":\{\"int\":)\w+', line)
#        print p
        tid = p.group(0)
#        print tid
        if (write_threads.get(tid)):
            line = f.readline()
            continue
        write_threads[tid] = "added"

    line = re.sub(r'\"sequence":[0-9\,]+', r'', line)
    line = re.sub(r'\"sequence":\{"long":[0-9]+\}\,', r'', line)
    line = re.sub(r'\"threadId":[0-9\,]+', r'', line)
    line = re.sub(r'\"threadId":\{"int":[0-9]+\}\,', r'', line)
    line = re.sub(r'\"start\ time\":\"[\-0-9\.]+\"', r'', line)
    line = re.sub(r'\"end\ time\":\"[\-0-9\.]+\"', r'', line)
    line = re.sub(r'\"uuid\":\"[0-9a-f]+\"\,?', r'', line)
    line = re.sub(r'\"startTimestampNanos\":[0-9\.\,]+', r'', line)
    line = re.sub(r'\"timestampNanos\":[0-9\.\,]+', r'', line)
    line = re.sub(r'\"tsNanos\":[0-9]+', r'', line)
    line = re.sub(r'\"com.bbn.tc.schema.avro.UUID\":\"[0-9a-f]+\"', r'', line)
    line = re.sub(r'\"subject\":\{\"com.bbn.tc.schema.avro.cdm18.UUID\":\"[0-9a-f]+\"\}\,', r'', line)
    line = re.sub(r'\"com.bbn.tc.schema.avro.cdm18.UUID\":\"[0-9a-f]+\"', r'', line)
    line = re.sub(r'\"subject\":\"[0-9a-f]+\"\,?', r'', line)
    line = re.sub(r'\"localPrincipal\":\"[0-9a-f]+\"\,?', r'', line)
    line = re.sub(r'\"pid\":\"[0-9]+\"\,?', r'', line)
    line = re.sub(r'\"long\":\"[0-9]+\"\,?', r'', line)
    line = re.sub(r'\"long\":[0-9\,]+', r'', line)
    line = re.sub(r'\"ppid\":\"[0-9]+\"\,?', r'', line)
    line = re.sub(r'\"tgid\":\"[0-9]+\"\,?', r'', line)
    line = re.sub(r'\/proc\/[0-9]+\/', r'/proc/', line)
    line = re.sub(r'\"cid\":[0-9\,]+', r'', line)
    line = re.sub(r'\"cwd\":\"[0-9a-z\/]+\",?', r'', line)
    line = re.sub(r'\"mode\":\"[0-9]+\",?', r'', line)
    line = re.sub(r'\"memoryAddress\":[0-9\,]+', r'', line)
    line = re.sub(r'\"localPort\":[0-9\,]+', r'', line)
    line = re.sub(r'\"seen time\":\"[0-9\.]+\"', r'', line)
    line = re.sub(r'\"remotePort\":[0-9\,]+', r'', line)
    line = re.sub(r'\"[\/a-z0-9]+unit\_tests\/tests', r'unit_tests/tests', line)
    line = re.sub(r'\"EVENT_WRITE\":\"[0-9]+\"', r'\"EVENT_WRITE\"', line)
    line = re.sub(r'\"EVENT_READ\":\"[0-9]+\"', r'\"EVENT_READ\"', line)
    line = re.sub(r'\"string\":\"[0-9a-zA-Z\-\_\/]+linux\/audit_cdm\/behavior\/tests\/', r'"string":"', line)
    line = re.sub(r'\"userId":\"[0-9]+\"', r'"userId"', line)
    line = re.sub(r'\"hostId":\"[0-9a-f]+\"\,', r'', line)
    line = re.sub(r'\"euid":\"[0-9]+\"', r'"euid"', line)
    line = re.sub(r'\"groupIds":\[[0-9\,\"]+\]', r'"groupIds:[]"', line)

    line = re.sub(r'cdm18.Host(.*)', 'cdm18.Host\"}}', line)
## remove these after cdm17 comparison
#    line = re.sub(r'\"localAddress\":\"127.0.0.1\"', r'"localAddress":""', line)
#    line = re.sub(r'\"StartMarker":\"[0-9]+\",?', r'', line)
#    line = re.sub(r'\"EndMarker":\"[0-9]+\",?', r'', line)
#    line = re.sub(r'\"TimeMarker":\"[0-9]+\",?', r'', line)

    print line
    line = f.readline()
     
f.close()
