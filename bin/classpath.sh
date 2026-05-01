#!/bin/bash

spade_home_path="$(cd "$( dirname "${BASH_SOURCE[0]}" )"/../ && pwd)"

os_name=$(uname)

classpath_separator=":"

if [[ "${os_name}" = *"CYGWIN"* ]]
then
  spade_home_path=`cygpath -w $spade_home_path`
  classpath_separator=";"
fi

deps=$(cd "${spade_home_path}" && mvn -q --no-transfer-progress dependency:build-classpath \
    -Dmdep.outputFile=/dev/stdout 2>/dev/null)

echo "${spade_home_path}/build${classpath_separator}${deps}"
