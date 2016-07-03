#!/bin/bash

set -x

_dir=`pwd`				# Current absolute path
_top=../..				# Relative root directory

_sconf=$_top/cfg/spade.config		# SPADE config file
_pidfile=$_top/spade.pid		# SPADE pid file
_avrojar=$_top/lib/avro-tools-1.8.1.jar	# Apache Avro Tools jar

_waitForStop () {
    while true; do

	# If this script started SPADE, check the pid instead of the
	# existence of the pid file (-- the latter is problematic due
	# to an apparent race condition when "quickly" stopping and
	# starting SPADE. :()

	if   [ "${_pid:-}" != "" ]; then
	    if [ `ps $_pid | wc -l` -lt 2 ]; then
		break
	    fi
	elif [ ! -e $_pidfile ]; then
	    break
	fi

	sleep 1
    done
}

_spadeStop  () {
    $_top/bin/spade stop 2>/dev/null 1>&2
    _waitForStop
}

_spadeStart  () {

    # Because the pid file existence is unreliable, extract the pid
    # from the SPADE start up message and use it to verify SPADE
    # termination.

    _tmpfile=/tmp/_tmpfile$$
    $_top/bin/spade start 2>$_tmpfile 1>&2

    # HACK: wait for SPADE start and log file creation :(

    sleep 5

    _pid=`sed -E 's/.+: //' $_tmpfile`		# current SPADE PID
    rm $_tmpfile

    # N.b.: the following may (randomly) fail because the log
    # file may not exist!

    _slog=`ls -1t $_top/log/*.log | head -1`	# current SPADE log
}

_waitForFinish () {

    # Poll for "run Audit log processing succeeded" diagnostic

    while true; do
	if [ `grep -c 'run Audit log processing succeeded' $_slog` -ne 0 ]; then
	    rm $_slog
	    break
	else
	    sleep 2
	fi
    done
}

_nGood=0
_nBad=0

for log in input/*.log; do

    _spadeStop

    echo "Processing $log..."

    _bname=`basename $log .log`		# base name of log
    _cdm=$_bname.cdm			# CDM storage file
    _json=$_bname.json			# json version of AVRO file
    _cdmhash=checksum/$_bname.hash	# Comparison hash of CDM storage

    # Write directives to the SPADE configuration file

cat <<EOF > $_sconf
add storage CDM output=$_dir/$_cdm
add reporter Audit inputLog=$_dir/$log arch=64 units=true fileIO=true netIO=true
EOF

    _spadeStart
	_waitForFinish
    _spadeStop

    # Clean out intermediate log, configuration

    rm -f $_dir/$log.*
    cat /dev/null > $_sconf

    # Calculate and save or compare hash of the AVRO JSON file because
    # binary AVRO files will be different given the same input (-- see
    #   https://avro.apache.org/docs/1.8.0/spec.html#Object+Container+Files
    # "Object Container Files", which states that the file header will
    # contain a "16-byte, randomly-generated sync marker for this
    # file.")

    java -jar $_avrojar tojson $_cdm > $_json 2>/dev/null

    _chksum=`sha256sum $_json | cut -d' ' -f1`
    rm $_cdm $_json

    # If a command line argument is provided, save the checksum

    if [ $# -ne 0 ]; then
	rm -f $_cdmhash
        echo "$_chksum" > $_cdmhash
	chmod 400 $_cdmhash

    # Otherwise, compare the checksums

    else
	if [ "$_chksum" != "`cat $_cdmhash`" ]; then
	  _nBad=$(( $_nBad + 1 ))
	else
	  _nGood=$(( $_nGood + 1 ))
        fi
    fi

done

if [ $# -ne 0 ]; then
    echo "Generated checksum files."
    exit 0
fi

_nTotal=$(( $_nBad + $_nGood ))

echo "--------------------------------------"
echo "Total tests: $_nTotal, # good: $_nGood, # bad: $_nBad"

if [ $_nBad -ne 0 ]; then
    exit 1
else
    exit 0
fi
