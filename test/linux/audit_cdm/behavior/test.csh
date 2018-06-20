#!/bin/csh
./normalize_cdm.py $1 |sort > tmp.json
sort $1".master" |diff - tmp.json 
