#!/bin/bash

BIN_DIR=test/asset/bin/ns

RUN_AS_USER=audited-user

function print_usage_and_exit()
{
    echo "Usage: $0 [<username>]"
    echo "  username: run as user. Default: ${RUN_AS_USER}"
    echo "Example: $0 ${RUN_AS_USER}"
    exit 1
}

function parse_args_and_set_globals()
{
    if [ "$#" -eq 1 ]; then
        RUN_AS_USER=$1
    elif [ "$#" -eq 0 ]; then
        # Using defaults
        return
    else
        print_usage_and_exit
    fi
}

function _run()
{
    su -mp ${RUN_AS_USER} -c "${BIN_DIR}/ns"
}

function main()
{
    _run

    echo "Test completed"
}

parse_args_and_set_globals $@
main
