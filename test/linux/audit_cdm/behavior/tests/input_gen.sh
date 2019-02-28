#!/bin/bash

if [ ! -e /tmp/input ]; then
    mkdir /tmp/input
    mkdir /tmp/output
    for i in {0..100}
    do
	echo hello $i  > "/tmp/input/data$(printf "%02d" "$i").txt"
    done
fi
