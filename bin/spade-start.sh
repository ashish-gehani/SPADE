#!/bin/bash
SPADE_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/../ && pwd )"
pushd ${SPADE_ROOT}
nohup java -cp "./build:./lib/*" -server -Xms128M -Xmx512M spade.core.Kernel &
popd
