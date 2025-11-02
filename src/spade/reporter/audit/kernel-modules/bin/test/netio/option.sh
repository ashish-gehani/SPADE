#!/bin/bash

KM_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/../../../ && pwd )"

# Function to get PIDs of audit and SPADE-related processes
# Usage: pids=$(get_audit_process_pids)
# Returns: Comma-separated string of PIDs (e.g., "1234,5678,9012")
function get_audit_process_pids()
{
    local pids=()

    # Get auditd PID
    local auditd_pid=$(pgrep auditd)
    [ -n "$auditd_pid" ] && pids+=($auditd_pid)

    # Get audispd PID
    local audispd_pid=$(pgrep audispd)
    [ -n "$audispd_pid" ] && pids+=($audispd_pid)

    # Get kauditd PID (kernel thread)
    local kauditd_pid=$(pgrep kauditd)
    [ -n "$kauditd_pid" ] && pids+=($kauditd_pid)

    # Get spadeAuditBridge PID
    local bridge_pid=$(pgrep -f spadeAuditBridge)
    [ -n "$bridge_pid" ] && pids+=($bridge_pid)

    # Get Java process with main class spade.core.Kernel
    local java_pids=$(pgrep -f "java.*spade\.core\.Kernel")
    [ -n "$java_pids" ] && pids+=($java_pids)

    # Join PIDs with commas (no spaces)
    local result=$(IFS=,; echo "${pids[*]}")
    echo "$result"
}

# Function to get formatted ignore_pids string
# Usage: ignore_string=$(get_ignore_pids_string)
# Returns: String in format 'ignore_pids="1234,5678,9012"'
function get_ignore_pids_option()
{
    local pids=$(get_audit_process_pids)
    echo "pid_trace_mode=1 pids=\"$pids\""
}

# Function to get formatted ignore_ppids string
# Usage: ignore_string=$(get_ignore_ppids_option)
# Returns: String in format 'ignore_ppids="1234,5678,9012"'
function get_ignore_ppids_option()
{
    local pids=$(get_audit_process_pids)
    echo "ppid_trace_mode=1 ppids=\"$pids\""
}

# Function to get combined ignore_pids and ignore_ppids options
# Usage: ignore_string=$(get_ignore_processes_options)
# Returns: String in format 'ignore_pids="1234,5678,9012" ignore_ppids="1234,5678,9012"'
function get_ignore_processes_options()
{
    local pids_option=$(get_ignore_pids_option)
    local ppids_option=$(get_ignore_ppids_option)
    echo "$pids_option $ppids_option"
}

# Function to get options to capture a given user by name
# Usage: user_options=$(get_user_capture_options [username])
# Returns: String in format 'uids=1000 uid_trace_mode=0' or empty string if user not found
# Default username is 'audited-user' if not provided
function get_user_capture_options()
{
    local username="${1:-audited-user}"

    # Get user ID for the given username
    local uid=$(id -u "$username" 2>/dev/null)

    if [ -z "$uid" ]; then
        echo "Error: user '$username' not found" >&2
        return 1
    fi

    echo "uids=$uid uid_trace_mode=0"
}

# Function to get options to ignore/exclude the current user
# Usage: user_options=$(get_user_ignore_options)
# Returns: String in format 'uids=1000 uid_trace_mode=1'
function get_user_ignore_options()
{
    # Get current user's UID
    local uid=$(id -u)

    echo "uids=$uid uid_trace_mode=1"
}

# Function to get functions monitoring option
# Usage: func_monitoring_option=$(get_function_monitoring_option)
# Returns: String 'log_syscalls=1'
function get_function_monitoring_option()
{
    echo "log_syscalls=1"
}

# Function to get option to include namespaces
# Usage: namespaces_option=$(get_include_namespaces_option)
# Returns: String 'namespaces=1'
function get_include_namespaces_option()
{
    echo "namespaces=1"
}

# Function to get option to exclude namespaces
# Usage: namespaces_option=$(get_exclude_namespaces_option)
# Returns: String 'namespaces=0'
function get_exclude_namespaces_option()
{
    echo "namespaces=0"
}

# Function to get option to include network I/O
# Usage: net_io_option=$(get_include_network_io_option)
# Returns: String 'net_io=1'
function get_include_network_io_option()
{
    echo "net_io=1"
}

# Function to get option to exclude network I/O
# Usage: net_io_option=$(get_exclude_network_io_option)
# Returns: String 'net_io=0'
function get_exclude_network_io_option()
{
    echo "net_io=0"
}

# Function to get option to include netfilter handle user
# Usage: nf_option=$(get_include_nf_handle_user_option)
# Returns: String 'nf_handle_user=1'
function get_include_nf_handle_user_option()
{
    echo "nf_handle_user=1"
}

# Function to get option to exclude netfilter handle user
# Usage: nf_option=$(get_exclude_nf_handle_user_option)
# Returns: String 'nf_handle_user=0'
function get_exclude_nf_handle_user_option()
{
    echo "nf_handle_user=0"
}

# Function to get option to include netfilter hooks
# Usage: nf_hooks_option=$(get_include_nf_hooks_option)
# Returns: String 'nf_hooks=1'
function get_include_nf_hooks_option()
{
    echo "nf_hooks=1"
}

# Function to get option to exclude netfilter hooks
# Usage: nf_hooks_option=$(get_exclude_nf_hooks_option)
# Returns: String 'nf_hooks=0'
function get_exclude_nf_hooks_option()
{
    echo "nf_hooks=0"
}

# Function to get option to monitor all connection states
# Usage: nf_monitor_ct_option=$(get_nf_monitor_ct_all_option)
# Returns: String 'nf_hooks_log_all_ct=-1'
function get_nf_monitor_ct_all_option()
{
    echo "nf_hooks_log_all_ct=-1"
}

# Function to get option to monitor only new connections
# Usage: nf_monitor_ct_option=$(get_nf_monitor_ct_only_new_option)
# Returns: String 'nf_hooks_log_all_ct=0'
function get_nf_monitor_ct_only_new_option()
{
    echo "nf_hooks_log_all_ct=0"
}

# Function to get config file option
# Usage: config_file_option=$(get_config_file_option [path])
# Returns: String in format 'config_file="/path/to/config"'
# Default path is '/opt/spade/audit/audit.config' if not provided
function get_config_file_option()
{
    local dev_config_file_path="${KM_ROOT}/cfg/spade_audit.config"
    local config_path="${1:-${dev_config_file_path}}"
    echo "config_file=\"$config_path\""
}

function get_dry_run_option()
{
    echo "dry_run=0"
}

# Function to get combined options for watching the audited user
# Usage: options=$(get_option_for_watch_audited_user)
# Returns: Combined string of all monitoring options separated by spaces
function get_option_for_watch_audited_user()
{
    local nf_hooks=$(get_include_nf_hooks_option)
    local nf_handle_user=$(get_include_nf_handle_user_option)
    local nf_monitor_ct=$(get_nf_monitor_ct_all_option)
    local network_io=$(get_include_network_io_option)
    local namespaces=$(get_include_namespaces_option)
    local function_monitoring=$(get_function_monitoring_option)
    local user_capture=$(get_user_capture_options)
    local ignore_processes=$(get_ignore_processes_options)
    local config_file=$(get_config_file_option)
    local dry_run=$(get_dry_run_option)

    echo "$nf_handle_user $network_io $namespaces $function_monitoring $user_capture $ignore_processes $nf_hooks $nf_monitor_ct $config_file $dry_run"
}

# Function to get options based on a command
# Usage: options=$(get_options_for_command "watch_audited_user")
# Returns: Combined options string for the specified command
function get_options_for_command()
{
    local command="$1"

    case "$command" in
        watch_audited_user)
            get_option_for_watch_audited_user
            ;;
        *)
            echo "Error: unknown command '$command'" >&2
            return 1
            ;;
    esac
}

# Main function
# Usage: ./option.sh <command>
# Example: ./option.sh watch_audited_user
function main()
{
    if [ $# -eq 0 ]; then
        echo "Error: no command provided" >&2
        echo "Usage: $0 <command>" >&2
        return 1
    fi

    get_options_for_command "$1"
}

# Run main function if script is executed directly
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    main "$@"
fi
