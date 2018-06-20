#!/bin/csh


#comment this line if you only want to generate
#master files for a selected scripts.

rm -f $1/*.master

if (-e $1/input) then
   cp -r $1/input /tmp
endif

foreach file (`ls $PWD/$1/*.bin`)
  echo $file
  if (! -e $file".json.master") then
   sudo ./gen_audit_spade.sh $file >& /dev/null
   sleep 5
   ./normalize_cdm.py $file".json" > $file".json.master"
    cp $file".json"  $file".json.master.saved"
  endif
end

sudo rm -rf /tmp/input
sudo rm -rf /tmp/output
