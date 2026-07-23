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

# Function to get first event ID at or after a given time from a log file
# Usage: get_first_event_id_at_time "log_file" "start_time"
# Time format: HH:MM:SS or timestamp
# Returns: event_id (just the number) to stdout
function get_first_event_id_at_time()
{
    local log_file="$1"
    local start_time="$2"

    if [ -z "$log_file" ] || [ -z "$start_time" ]; then
        echo "Error: Log file and start time are required" >&2
        echo "Usage: get_first_event_id_at_time <log_file> <start_time>" >&2
        echo "Time format: HH:MM:SS or timestamp" >&2
        return 1
    fi

    if [ ! -f "$log_file" ]; then
        echo "Error: Log file '$log_file' not found" >&2
        return 1
    fi

    # Convert time to timestamp (seconds since epoch)
    # Handle both HH:MM:SS format and absolute timestamps
    local target_timestamp
    if [[ "$start_time" =~ ^[0-9]+$ ]]; then
        # Already a timestamp
        target_timestamp="$start_time"
    else
        # Convert HH:MM:SS to timestamp
        target_timestamp=$(date -d "$start_time" +%s 2>/dev/null)
        if [ $? -ne 0 ]; then
            echo "Error: Invalid time format '$start_time'" >&2
            return 1
        fi
    fi

    # Extract first event ID at or after the target time
    # Format: msg=audit(timestamp.milliseconds:event_id)
    local event_id=$(awk -v target="$target_timestamp" '
        match($0, /msg=audit\(([0-9]+)\.([0-9]+):([0-9]+)\)/, arr) {
            timestamp = arr[1]
            event_id = arr[3]
            if (timestamp >= target) {
                print event_id
                exit
            }
        }
    ' "$log_file")

    if [ -z "$event_id" ]; then
        echo "Error: No events found at or after time $start_time" >&2
        return 1
    fi

    echo "$event_id"
    return 0
}

# Function to get all events after a specific event ID from a log file
# Usage: get_events_after_id "log_file" "event_id"
# Outputs all matching records to stdout
function get_events_after_id()
{
    local log_file="$1"
    local start_event_id="$2"

    if [ -z "$log_file" ] || [ -z "$start_event_id" ]; then
        echo "Error: Log file and event ID are required" >&2
        echo "Usage: get_events_after_id <log_file> <event_id>" >&2
        return 1
    fi

    if [ ! -f "$log_file" ]; then
        echo "Error: Log file '$log_file' not found" >&2
        return 1
    fi

    # Extract all records with event ID >= start_event_id
    awk -v start_id="$start_event_id" '
        match($0, /msg=audit\([0-9]+\.[0-9]+:([0-9]+)\)/, arr) {
            event_id = arr[1]
            if (event_id >= start_id) {
                print $0
            }
        }
    ' "$log_file"

    return 0
}

# Function to get audit logs from a file after a given time (using event ID method)
# Usage: get_audit_logs_from_file "log_file" "start_time"
# Time format: HH:MM:SS or timestamp
# This is similar to get_audit_logs_after but works on log files using event IDs
function get_audit_logs_from_file()
{
    local log_file="$1"
    local start_time="$2"

    if [ -z "$log_file" ] || [ -z "$start_time" ]; then
        echo "Error: Log file and start time are required" >&2
        echo "Usage: get_audit_logs_from_file <log_file> <start_time>" >&2
        echo "Time format: HH:MM:SS or timestamp" >&2
        return 1
    fi

    # Get the first event ID at or after the start time
    local event_id=$(get_first_event_id_at_time "$log_file" "$start_time")
    if [ $? -ne 0 ]; then
        return 1
    fi

    # Get all events after that event ID (output goes to stdout)
    get_events_after_id "$log_file" "$event_id"
    return $?
}

# Function to get audit logs after a given time (using event ID-based filtering)
# Usage: get_audit_logs_after "start_time" [log_file]
# Time format: HH:MM:SS or timestamp
# If log_file is not provided, uses /var/log/audit/audit.log by default
function get_audit_logs_after()
{
    local start_time="$1"
    local log_file="${2:-/var/log/audit/audit.log}"

    if [ -z "$start_time" ]; then
        echo "Error: Start time is required" >&2
        echo "Usage: get_audit_logs_after <start_time> [log_file]" >&2
        echo "Time format: HH:MM:SS or timestamp" >&2
        echo "Default log_file: /var/log/audit/audit.log" >&2
        return 1
    fi

    if [ ! -f "$log_file" ]; then
        echo "Error: Log file '$log_file' not found" >&2
        return 1
    fi

    # Get the first event ID at or after the start time
    local event_id=$(get_first_event_id_at_time "$log_file" "$start_time")
    if [ $? -ne 0 ]; then
        return 1
    fi

    # Get all events after that event ID (output goes to stdout)
    get_events_after_id "$log_file" "$event_id"
    return $?
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
        logfile)
            get_audit_logs_from_file "$@"
            ;;
        eventid)
            get_first_event_id_at_time "$@"
            ;;
        eventsafter)
            get_events_after_id "$@"
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
            echo "Usage: $0 {stop|start|log|logfile|eventid|eventsafter|check|time|clear} [args]"
            echo ""
            echo "Commands:"
            echo "  stop                      - Stop audit service"
            echo "  start                     - Start audit service"
            echo "  log <time> [file]         - Get audit logs after time (default: /var/log/audit/audit.log)"
            echo "  logfile <file> <time>     - Get audit logs from file after time (same as 'log' but explicit)"
            echo "  eventid <file> <time>     - Get first event ID at/after specified time"
            echo "  eventsafter <file> <id>   - Get all events after specified event ID"
            echo "  check                     - Check if audit service is running"
            echo "  time                      - Get current time in HH:MM:SS format"
            echo "  clear                     - Clear all audit rules"
            return 1
            ;;
    esac
}

# Execute main function with all arguments
main "$@"
