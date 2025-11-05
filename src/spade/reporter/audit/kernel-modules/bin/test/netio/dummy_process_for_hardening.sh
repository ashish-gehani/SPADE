#!/bin/bash

# Script to run a dummy process named harden_spade_audit_dummy_process
# This process will run indefinitely until killed by a signal

# Process name constant
PROCESS_NAME="harden_spade_audit_dummy_process"

# Function to get the PID of the running process
# Usage: pid=$(get_pid)
# Returns: PID of the process or empty string if not found
function get_pid()
{
    pgrep -f "^${PROCESS_NAME} "
}

# Function to start the dummy process
# Usage: start
function start()
{
    # Check if already running
    local existing_pid=$(get_pid)
    if [ -n "$existing_pid" ]; then
        echo "Error: ${PROCESS_NAME} is already running with PID: $existing_pid" >&2
        return 1
    fi

    # Start the process in the background with nohup
    nohup /bin/bash -c '
        exec -a "'"$PROCESS_NAME"'" /bin/bash -c '\''
            trap "exit 0" SIGTERM SIGINT
            while true; do sleep 1; done
        '\''
    ' > /dev/null 2>&1 &

    sleep 1

    # Get and display the PID
    local new_pid=$(get_pid)
    if [ -n "$new_pid" ]; then
        echo "${PROCESS_NAME} started with PID: $new_pid"
    else
        echo "Error: Failed to start ${PROCESS_NAME}" >&2
        return 1
    fi
}

# Function to kill the running process
# Usage: kill_process
function kill_process()
{
    local pid=$(get_pid)

    if [ -z "$pid" ]; then
        echo "Error: ${PROCESS_NAME} is not running" >&2
        return 1
    fi

    echo "Killing ${PROCESS_NAME} (PID: $pid)..."
    kill "$pid"

    # Wait a moment and check if it's still running
    sleep 1
    if [ -n "$(get_pid)" ]; then
        echo "Process did not terminate, forcing kill..."
        kill -9 "$pid"
    else
        echo "Process terminated successfully"
    fi
}

# Main function
# Usage: ./dummy_process_for_hardening.sh <command>
# Commands: start, kill, pid
function main()
{
    if [ $# -eq 0 ]; then
        echo "Usage: $0 {start|kill|pid}" >&2
        return 1
    fi

    local command="$1"

    case "$command" in
        start)
            start
            ;;
        kill)
            kill_process
            ;;
        pid)
            local pid=$(get_pid)
            if [ -n "$pid" ]; then
                echo "$pid"
            else
                echo "Error: ${PROCESS_NAME} is not running" >&2
                return 1
            fi
            ;;
        *)
            echo "Error: unknown command '$command'" >&2
            echo "Usage: $0 {start|kill|pid}" >&2
            return 1
            ;;
    esac
}

# Run main function if script is executed directly
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    main "$@"
fi
