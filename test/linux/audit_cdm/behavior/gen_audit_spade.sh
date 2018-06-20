#!/bin/bash

TESTUSER="local"
SPADE_HOME="/home/<user>/SPADE"


_id=`id|cut -d"=" -f2 |cut -d"(" -f1`

if [ $_id != 0 ]; then 
  echo "This program must be run as root"
  exit
fi

_uid=`su - $TESTUSER -c "id" |cut -d"=" -f2 |cut -d"(" -f1`
echo $_uid
_tm=`date +%s`
if [ -e /var/log/audit/audit.log ]; then
   mv /var/log/audit/audit.log "/var/log/audit/audit.old."$_tm
fi

/etc/init.d/auditd restart
auditctl -D
# auditctl -a exit,always -F arch=b64 -F success=1 -F uid=$_uid -S all
# the never rule should always be before all other rules (if being appended)
#auditctl -a exit,never -F arch=b64 -S socket -S bind -S connect -S accept -S accept4 -S sendmsg -S sendto -S recvfrom -S recvmsg
#auditctl -a exit,always -F arch=b64 -F uid=$_uid -S exit -S exit_group -S kill
#auditctl -a exit,always -F arch=b64 -F success=1 -F uid=$_uid -S all
#auditctl -l

sleep 2

cd $SPADE_HOME

echo "add reporter Audit syscall=all fileIO=true netIO=true units=true user="$TESTUSER" versions=false outputLog="$1".log" > cfg/spade.config

sleep 2
bin/spade start
sleep 5

echo "su - $TESTUSER -c $1"
su - $TESTUSER -c $1
sleep 5
bin/spade stop

auditctl -D
#mv /var/log/audit/audit.log $1".log"
#chmod +r $1".log"

sleep 2

#extract the EXECVE call
ln=`grep -n "syscall=59" $1".log" |grep -v "bash" |cut -d":" -f1 |head -1`
echo $ln

ln=$(($ln+2))

head -$ln $1".log" |tail -3  > $1".extract.log"


#extract all entries between the getpid call and progam end
begin=`grep -n "syscall=39" $1".log" |grep -v "bash" |head -1 |cut -d":" -f1`
end=`grep -n "PAM:session_close" $1".log" |grep $TESTUSER |head -1 |cut -d":" -f1`

echo "begin = " $begin 
echo "end = " $end

end=$(($end-1))
cnt=$(($end-$begin))

head -$end $1".log" |tail -$cnt  >> $1".extract.log"


# create spade config and run spade


cd $SPADE_HOME

echo "add storage Graphviz "$1".dot" > cfg/spade.config
echo "add storage CDM hexUUIDs=true output="$1".json" >> cfg/spade.config
echo "add reporter Audit inputLog="$1".extract.log fileIO=true netIO=true units=true arch=64 versions=false handleLocalEndpoints=true" >> cfg/spade.config

bin/spade start
sleep 5
bin/spade stop

#dot -Tpng $1".dot" > $1".png"




