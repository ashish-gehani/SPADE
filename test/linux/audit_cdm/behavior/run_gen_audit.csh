#!/bin/csh


foreach file (`ls $PWD/$1/*.bin`)
  if (! -e $file".png") then
    echo "testing " $file
   sudo ./gen_audit.sh $file
  endif
end
