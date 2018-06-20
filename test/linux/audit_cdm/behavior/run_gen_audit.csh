#!/bin/csh


set pass_cnt = 0
set fail_cnt = 0

#comment this out if you want to control
#which tests you want to run.  Only those
#without a png file will be run

#rm -f $1/*.png

auditctl -s

if (-e $1/input) then
   cp -r $1/input /tmp
endif



foreach file (`ls $PWD/$1/*.bin`)
  if (! -e $file".png") then
   echo "testing " $file
   sudo ./gen_audit_spade.sh $file  >& /dev/null
   sleep 5
   ./normalize_cdm.py $file".json" |sort > tmp.json
   set cnt = `sort $file".json".master |diff - tmp.json |wc -l`
   if ($cnt > 0) then
     echo "FAILED"
     @ fail_cnt = $fail_cnt + 1
     auditctl -s
   else
     echo "PASSED"
     @ pass_cnt = $pass_cnt + 1
   endif
  endif
end

sudo rm -rf /tmp/input
sudo rm -rf /tmp/output


echo "Tests Passed: $pass_cnt; Tests failed: $fail_cnt"
