#!/bin/csh


#comment this line if you only want to generate
#master files for a selected scripts.

rm -f $1/*.master

foreach file (`ls $PWD/$1/*.bin`)
  echo $file
  if (! -e $file".json.master") then
   sudo ./gen_audit.sh $file >& /dev/null
   ./normalize_cdm.py $file".json" > $file".json.master"
  endif
end
