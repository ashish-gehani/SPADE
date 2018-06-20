#!/bin/csh


set pass_cnt = 0
set fail_cnt = 0


while (1)
    set index = `perl -e 'srand; print int(rand(38)+1)'`
    set file = `ls $PWD/$1/*.bin |tr ' ' '\n' |head -$index |tail -1`
    echo "Testing $file"
    sudo ./gen_audit_spade.sh $file >& /dev/null
    ./normalize_cdm.py $file".json" |sort > tmp.json
    set cnt = `sort $file".json".master |diff - tmp.json |wc -l`
    if ($cnt > 0) then
     echo "FAILED"
     @ fail_cnt = $fail_cnt + 1
    else
     echo "PASSED"
     @ pass_cnt = $pass_cnt + 1
   endif
  endif
end


echo "Tests Passed: $pass_cnt; Tests failed: $fail_cnt"
