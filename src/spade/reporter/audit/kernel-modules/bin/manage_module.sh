#!/bin/bash

# Function to check if a module is already loaded
# Usage: is_module_loaded <module_path>
# Returns: 0 if loaded, 1 if not loaded
function is_module_loaded()
{
    local module_path=$1
    local module_name

    if [ -z "$module_path" ]; then
        echo "Error: module path is required"
        return 1
    fi

    # Extract module name from path (remove directory and .ko extension)
    module_name=$(basename "$module_path" .ko)

    # Check if module is loaded
    if lsmod | grep -q "^${module_name}\s"; then
        echo "Module '$module_name' is already loaded"
        return 0
    else
        echo "Module '$module_name' is not loaded"
        return 1
    fi
}

# Function to insert a kernel module
# Usage: insmod_module <module_path> [module_args]
function insmod_module()
{
    local module_path=$1
    shift
    local module_args="$@"

    if [ -z "$module_path" ]; then
        echo "Error: module path is required"
        return 1
    fi

    if [ ! -f "$module_path" ]; then
        echo "Error: module file not found: $module_path"
        return 1
    fi

    echo "Inserting module: $module_path"
    if [ -n "$module_args" ]; then
        echo "Module arguments: $module_args"
        insmod "$module_path" $module_args
    else
        insmod "$module_path"
    fi

    local ret=$?
    if [ $ret -eq 0 ]; then
        echo "Module inserted successfully"
    else
        echo "Failed to insert module (exit code: $ret)"
    fi

    return $ret
}

# Function to remove a kernel module
# Usage: rmmod_module <module_path>
function rmmod_module()
{
    local module_path=$1
    local module_name

    if [ -z "$module_path" ]; then
        echo "Error: module path is required"
        return 1
    fi

    # Extract module name from path (remove directory and .ko extension)
    module_name=$(basename "$module_path" .ko)

    # Check if module is loaded
    if ! lsmod | grep -q "^${module_name}\s"; then
        echo "Module '$module_name' is not loaded"
        return 1
    fi

    echo "Removing module: $module_name"
    rmmod "$module_name"

    local ret=$?
    if [ $ret -eq 0 ]; then
        echo "Module removed successfully"
    else
        echo "Failed to remove module (exit code: $ret)"
    fi

    return $ret
}

# Function to get module syslog
# Usage: get_module_syslog <module_path> [syslog_file]
function get_module_syslog()
{
    local module_path=$1
    local syslog_file=${2:-/var/log/syslog}
    local module_name
    local script_dir

    if [ -z "$module_path" ]; then
        echo "Error: module path is required"
        return 1
    fi

    # Extract module name from path (remove directory and .ko extension)
    module_name=$(basename "$module_path" .ko)

    # Get the directory where this script is located
    script_dir=$(dirname "${BASH_SOURCE[0]}")

    # Check if read_module_syslog.sh exists
    if [ ! -f "$script_dir/read_module_syslog.sh" ]; then
        echo "Error: read_module_syslog.sh not found in $script_dir"
        return 1
    fi

    # Call read_module_syslog.sh with module name and syslog file
    bash "$script_dir/read_module_syslog.sh" "$module_name" "$syslog_file"

    return $?
}

# Main function to manage module operations
# Usage: manage_module <command> <module_path> [args...]
# Commands: insmod, rmmod, checkmod, logmod
function manage_module()
{
    local command=$1
    local module_path=$2
    shift 2
    local args="$@"

    if [ -z "$command" ]; then
        echo "Error: command is required"
        echo "Usage: manage_module <command> <module_path> [args...]"
        echo "Commands: insmod, rmmod, checkmod, logmod"
        return 1
    fi

    if [ -z "$module_path" ]; then
        echo "Error: module path is required"
        return 1
    fi

    case "$command" in
        insmod)
            insmod_module "$module_path" $args
            ;;
        rmmod)
            rmmod_module "$module_path"
            ;;
        checkmod)
            is_module_loaded "$module_path"
            ;;
        logmod)
            get_module_syslog "$module_path" $args
            ;;
        *)
            echo "Error: unknown command '$command'"
            echo "Valid commands: insmod, rmmod, checkmod, logmod"
            return 1
            ;;
    esac

    return $?
}

# If script is executed directly (not sourced), run manage_module with arguments
if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    manage_module "$@"
fi