#!/bin/bash

_id=`id|cut -d"=" -f2 |cut -d"(" -f1`

if [ $_id != 0 ]; then 
  echo "This program must be run as root"
  exit
fi

_uid=`su - local -c "id" |cut -d"=" -f2 |cut -d"(" -f1`
echo $_uid
_tm=`date +%s`
if [ -e /var/log/audit/audit.log ]; then
   mv /var/log/audit/audit.log "/var/log/audit/audit.old."$_tm
fi

/etc/init.d/auditd restart
auditctl -D
# auditctl -a exit,always -F arch=b64 -F success=1 -F uid=$_uid -S all
auditctl -a exit,always -F arch=b64 -F uid=$_uid -S exit -S exit_group -S kill
auditctl -a exit,always -F arch=b64 -F success=1 -F uid=$_uid -S all
sleep 2
echo "su - local -c $1"
su - local -c $1
sleep 2
auditctl -D
mv /var/log/audit/audit.log $1".log"
chmod +r $1".log"


#extract the EXECVE call
ln=`grep -n "syscall=59" $1".log" |grep -v "bash" |cut -d":" -f1 |head -1`

ln=$(($ln+2))

head -$ln $1".log" |tail -3  > $1".extract.log"


#extract all entries between the getpid call and progam end
begin=`grep -n "syscall=39" $1".log" |grep -v "bash" |head -1 |cut -d":" -f1`
end=`grep -n "PAM:session_close" $1".log" |head -1 |cut -d":" -f1`

echo "begin = " $begin 
echo "end = " $end

end=$(($end-1))
cnt=$(($end-$begin))

head -$end $1".log" |tail -$cnt  >> $1".extract.log"


# create spade config and run spade

cd $SPADE_HOME

echo "add storage Graphviz "$1".dot" > cfg/spade.config
echo "add storage CDM hexUUIDs=true output="$1".json" >> cfg/spade.config
echo "add reporter Audit inputLog="$1".extract.log fileIO=true netIO=true units=true arch=64 versions=false" >> cfg/spade.config

bin/spade start
sleep 5
bin/spade stop

dot -Tpng $1".dot" > $1".png"




