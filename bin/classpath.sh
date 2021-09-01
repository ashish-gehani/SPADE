#!/bin/bash

spade_home_path="$(cd "$( dirname "${BASH_SOURCE[0]}" )"/../ && pwd)"
spade_cfg_path="${spade_home_path}/cfg"
spade_classpath_file="${spade_cfg_path}/java.classpath"

if [ ! -f "${spade_classpath_file}" ]
then
  echo "Missing classpath entries file: ${spade_classpath_file}"
  exit 1
fi

os_name=$(uname)

is_cygwin=0

# default for all non-cygwin
classpath_separator=":"

if [[ "${os_name}" = *"CYGWIN"* ]]
then
  # update SPADE home path to windows format
  spade_home_path=`cygpath -w $SPADE_ROOT`
  classpath_separator=";"
  is_cygwin=1
fi

classpath_str=""

while IFS= read -r line
do
  path_with_spade_home="${spade_home_path}/${line}"
  classpath_str="${classpath_str}${path_with_spade_home}${classpath_separator}"
done < "${spade_classpath_file}"

if [ "$is_cygwin" -eq 1 ]
then
  classpath_str="${classpath_str//\//\\\\}"
fi

echo "${classpath_str::${#classpath_str}-1}"
