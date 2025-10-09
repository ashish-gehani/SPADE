#!/bin/bash

# Run network socket server and client test as the specified user
# Usage: ./run_net_test.sh <username> <tcp_port> <udp_port>

BIN_DIR=test/asset/bin/socket/net

RUN_AS_USER=audited-user
TCP_PORT=8090
UDP_PORT=8091

function print_usage_and_exit()
{
    echo "Usage: $0 [<username> <tcp_port> <udp_port>]"
    echo "  username: run as user. Default: ${RUN_AS_USER}"
    echo "  tcp_port: TCP port number. Default: ${TCP_PORT}"
    echo "  udp_port: UDP port number. Default: ${UDP_PORT}"
    echo "Example: $0 ${RUN_AS_USER} ${TCP_PORT} ${UDP_PORT}"
    exit 1
}

function parse_args_and_set_globals()
{
    if [ "$#" -eq 3 ]; then
        RUN_AS_USER=$1
        TCP_PORT=$2
        UDP_PORT=$3
    elif [ "$#" -eq 0 ]; then
        # Using defaults
        return
    else
        print_usage_and_exit
    fi
}

function _run()
{
    local ip_version=$1
    local ip=$2

    su -mp ${RUN_AS_USER} -c "${BIN_DIR}/server ${ip_version} ${TCP_PORT} ${UDP_PORT}" &
    SERVER_PID=$!

    sleep 2

    su -mp ${RUN_AS_USER} -c "${BIN_DIR}/client ${ip_version} ${ip} ${TCP_PORT} ${UDP_PORT}"

    wait ${SERVER_PID}
}

function main()
{
    _run 4 0.0.0.0
    _run 6 ::1

    echo "Test completed"
}

parse_args_and_set_globals $@
main
