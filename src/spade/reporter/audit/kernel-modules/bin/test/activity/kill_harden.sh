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

# Script to kill the harden dummy process as different users to generate activity

KM_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/../../../ && pwd )"

# Path to the harden dummy process script
HARDEN_DUMMY_SCRIPT="${KM_ROOT}/bin/test/netio/dummy_process_for_hardening.sh"

# Function to kill the harden dummy process as a specific user
# Usage: kill_as_user <uid>
function kill_as_user()
{
    local uid=$1
    local username=""

    # Get username from UID
    username=$(getent passwd "$uid" | cut -d: -f1)

    if [ -z "$username" ]; then
        echo "Warning: Could not find username for UID $uid"
        return 1
    fi

    echo "Attempting to kill harden dummy process as user $username (UID: $uid)..."

    # Check if harden dummy script exists
    if [ ! -f "$HARDEN_DUMMY_SCRIPT" ]; then
        echo "Error: Harden dummy process script not found at $HARDEN_DUMMY_SCRIPT"
        return 1
    fi

    # If running as root (UID 0), no need to use su
    if [ "$uid" -eq 0 ]; then
        bash "$HARDEN_DUMMY_SCRIPT" kill
        local result=$?
        if [ $result -ne 0 ]; then
            echo "Warning: Failed to kill harden dummy process as root"
            return $result
        fi
    else
        # Use su to run as the specified user
        su -mp "$username" -c "bash '$HARDEN_DUMMY_SCRIPT' kill"
        local result=$?
        if [ $result -ne 0 ]; then
            echo "Warning: Failed to kill harden dummy process as user $username (UID: $uid)"
            return $result
        fi
    fi

    echo "Successfully killed harden dummy process as user $username (UID: $uid)"
    return 0
}

# Main function
function main()
{
    echo "=== Kill Harden Dummy Process Activity Test ==="
    echo ""

    echo "Step 1: Kill as user 1000"
    kill_as_user 1000

    sleep 2

    echo ""
    echo "Step 2: Kill as user 0"
    kill_as_user 0

    echo ""
    echo "Test completed"
}

main
