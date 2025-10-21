#!/bin/bash

# Function to check if audit service is running
function is_audit_running()
{
    echo "=== Checking Audit Service Status ==="

    # Check if auditd is running using systemctl
    if systemctl is-active --quiet auditd; then
        echo "Audit service is running"
        return 0
    else
        echo "Audit service is not running"
        return 1
    fi
}

# Function to stop audit service
function stop_audit()
{
    echo "=== Stopping Audit Service ==="

    # Check if audit is running first
    if ! systemctl is-active --quiet auditd; then
        echo "Audit service is not running"
        return 0
    fi

    # Stop auditd service
    systemctl stop auditd
    if [ $? -eq 0 ]; then
        echo "Audit service stopped successfully"
        return 0
    else
        echo "Error: Failed to stop audit service"
        return 1
    fi
}

# Function to start audit service
function start_audit()
{
    echo "=== Starting Audit Service ==="

    # Check if audit is already running
    if systemctl is-active --quiet auditd; then
        echo "Audit service is already running"
        return 0
    fi

    # Start auditd service
    systemctl start auditd
    if [ $? -eq 0 ]; then
        echo "Audit service started successfully"
        return 0
    else
        echo "Error: Failed to start audit service"
        return 1
    fi
}

# Function to clear audit rules
function clear_audit_rules()
{
    echo "=== Clearing Audit Rules ==="

    # Delete all audit rules
    auditctl -D
    if [ $? -eq 0 ]; then
        echo "Audit rules cleared successfully"
        return 0
    else
        echo "Error: Failed to clear audit rules"
        return 1
    fi
}

# Function to get current time in format for ausearch
# Returns time in HH:MM:SS format
function get_current_time()
{
    date +%H:%M:%S
}

# Function to get audit logs after a given time
# Usage: get_audit_logs_after "start_time" [end_time]
# Time format: HH:MM:SS or "now", "recent", "today", "yesterday", etc.
function get_audit_logs_after()
{
    local start_time="$1"
    local end_time="$2"

    echo "=== Getting Audit Logs ==="

    if [ -z "$start_time" ]; then
        echo "Error: Start time is required"
        echo "Usage: get_audit_logs_after <start_time> [end_time]"
        echo "Time format: HH:MM:SS or 'now', 'recent', 'today', etc."
        return 1
    fi

    # Build ausearch command
    local cmd="ausearch --raw -ts $start_time"

    if [ -n "$end_time" ]; then
        cmd="$cmd -te $end_time"
    fi

    # Execute ausearch
    eval $cmd
    local result=$?

    if [ $result -eq 0 ]; then
        return 0
    else
        echo "No audit logs found or error occurred"
        return $result
    fi
}

# Main function to handle commands
function main()
{
    local command="$1"
    shift  # Remove first argument, leaving the rest as $@

    case "$command" in
        stop)
            stop_audit
            ;;
        start)
            start_audit
            ;;
        log)
            get_audit_logs_after "$@"
            ;;
        check)
            is_audit_running
            ;;
        time)
            get_current_time
            ;;
        clear)
            clear_audit_rules
            ;;
        *)
            echo "Usage: $0 {stop|start|log|check|time|clear} [args]"
            echo ""
            echo "Commands:"
            echo "  stop        - Stop audit service"
            echo "  start       - Start audit service"
            echo "  log <time>  - Get audit logs after specified time"
            echo "  check       - Check if audit service is running"
            echo "  time        - Get current time in HH:MM:SS format"
            echo "  clear       - Clear all audit rules"
            return 1
            ;;
    esac
}

# Execute main function with all arguments
main "$@"
