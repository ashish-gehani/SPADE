#!/bin/bash

#set -x

_dir=`pwd`

_sconf=../cfg/spade.config	# SPADE config file

_nGood=0
_nBad=0

for log in *.log; do
    ../bin/spade stop

    echo "Processing $log..."

    _bname=`basename $log .log`	# base name of log
    _cdm=$_bname.cdm		# CDM storage file
    _json=$_bname.json		# json version of AVRO file
    _cdmhash=$_bname.hash	# Comparison hash of CDM storage

cat <<EOF > $_sconf
storage CDM output=$_dir/$_cdm
reporter Audit inputLog=$_dir/$log arch=64 units=true fileio=true netio=true
EOF

    ../bin/spade start

    sleep 5

    _spid=`ps xwwww | grep spade.utility.Daemonizer | grep -v grep | cut -d' ' -f1`

    _slog=`ls -1t ../log/*.log | head -1`	# current SPADE log

    # Poll for "run Audit log processing succeeded" diagnostic

    while true; do
	if [ `grep -c 'run Audit log processing succeeded' $_slog` -ne 0 ]; then
	    break
	else
	    sleep 5
	fi
    done

    # Shut down SPADE

    ../bin/spade stop

    # Poll for SPADE termination

    while true; do
	if [ `ps $_spid | wc -l` -lt 2 ]; then
	    break
	else
	    sleep 5
	fi
    done

    cat /dev/null > $_sconf

    # Calculate and compare hash

    java -jar avro-tools-1.7.4.jar tojson $_cdm > $_json

    _chksum=`sha256sum $_json | cut -d' ' -f1`
    rm $_cdm $_json

    if [ $# -ne 0 ]; then
        echo "$_chksum" > $_cdmhash
    else
	if [ "$_chksum" != "`cat $_cdmhash`" ]; then
	  _nBad=$(( $_nBad + 1 ))
	else
	  _nGood=$(( $_nGood + 1 ))
        fi
    fi

done

if [ $# -ne 0 ]; then
    echo "Generated check sum files."
    exit 0
fi

_nTotal=$(( $_nBad + $_nGood ))

echo "Total tests: $_nTotal, #good: $_nGood, #bad: $_nBad"

if [ $_nBad -ne 0 ]; then
    exit 1
else
    exit 0
fi
