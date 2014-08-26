#!/bin/bash
SPADE_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/../ && pwd )"
pushd ${SPADE_ROOT}
java -cp './build:./lib/*' spade.client.QueryClient
popd
