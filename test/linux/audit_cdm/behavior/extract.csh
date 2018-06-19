#!/bin/bash


#extract the EXECVE call
ln=`grep -n "syscall=59" $1 |grep -v "bash" |cut -d":" -f1 |head -1`
echo $ln

ln=$(($ln+2))

head -$ln $1 |tail -3  > $1".bar"


#extract all entries between the getpid call and progam end
begin=`grep -n "syscall=39" $1 |grep -v "bash" |head -1 |cut -d":" -f1`
end=`grep -n "PAM:session_close" $1 |head -1 |cut -d":" -f1`

echo "begin = " $begin 
echo "end = " $end

end=$(($end-1))
cnt=$(($end-$begin))

head -$end $1 |tail -$cnt  >> $1".bar"





