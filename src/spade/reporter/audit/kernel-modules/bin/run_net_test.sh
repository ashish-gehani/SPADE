#!/bin/bash
#
#  --------------------------------------------------------------------------------
#  SPADE - Support for Provenance Auditing in Distributed Environments.
#  Copyright (C) 2025 SRI International

#  This program is free software: you can redistribute it and/or
#  modify it under the terms of the GNU General Public License as
#  published by the Free Software Foundation, either version 3 of the
#  License, or (at your option) any later version.

#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
#  General Public License for more details.

#  You should have received a copy of the GNU General Public License
#  along with this program. If not, see <http://www.gnu.org/licenses/>.
#  --------------------------------------------------------------------------------

KM_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/../ && pwd )"

BIN_DIR="${KM_ROOT}/test/asset/bin/socket/net"

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
