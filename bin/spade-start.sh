#!/bin/bash
SPADE_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/../ && pwd )"
pushd ${SPADE_ROOT} > /dev/null
nohup java -cp "./build:./lib/*" -server -Xms128M -Xmx512M spade.core.Kernel > /dev/null 2>&1 &
popd > /dev/null
