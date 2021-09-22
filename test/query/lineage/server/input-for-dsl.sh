#!/bin/bash

spade_pipe=/tmp/spade_pipe
#spade_pipe=/dev/fd/1

echo type:Process host:src id:1002 pid:2 > "${spade_pipe}"
echo type:Process host:src id:1001 pid:1 >> "${spade_pipe}"
echo type:Artifact subtype:file host:src id:1003 path:/etc/passwd >> "${spade_pipe}"
echo type:Artifact subtype:network\ socket host:src id:1004 local\ address:0.0.0.0 local\ port:1221 remote\ address:192.168.2.202 remote\ port:2112 >> "${spade_pipe}"
echo type:WasTriggeredBy operation:fork host:src from:1001 to:1002 time:1 >> "${spade_pipe}"
echo type:Used operation:read host:src from:1002 to:1003 time:2 >> "${spade_pipe}"
echo type:WasGeneratedBy operation:write host:src from:1004 to:1001 time:3 >> "${spade_pipe}"
