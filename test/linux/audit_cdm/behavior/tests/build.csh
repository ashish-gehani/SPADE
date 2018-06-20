#!/bin/csh
foreach file (`ls *.c`)
  set name = `echo $file |cut -d"." -f1`
  gcc $file -o $name".bin"
end
./input_gen.sh
