#!/bin/bash

spade_pipe=/tmp/spade_pipe
#spade_pipe=/dev/fd/1

echo type:Process host:dst id:2 pid:2 > "${spade_pipe}"
echo type:Process host:dst id:1 pid:1 >> "${spade_pipe}"
echo type:Artifact subtype:file host:dst id:3 path:/etc/passwd >> "${spade_pipe}"
echo type:Artifact subtype:network\ socket host:dst id:4 local\ address:192.168.2.202 local\ port:2112 remote\ address:0.0.0.0 remote\ port:1221 >> "${spade_pipe}"
echo type:WasTriggeredBy operation:fork host:dst from:2 to:1 time:1 >> "${spade_pipe}"
echo type:Used operation:read host:dst from:1 to:4 time:2 >> "${spade_pipe}"
echo type:WasGeneratedBy operation:write host:dst from:3 to:2 time:3 >> "${spade_pipe}"
