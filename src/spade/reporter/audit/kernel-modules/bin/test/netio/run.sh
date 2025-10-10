#!/bin/bash

# Function to get the netio module path
# Usage: get_module_path
function get_module_path()
{
    echo "./build/netio.ko"
}

# Function to get the manage script path
# Usage: get_manage_script
function get_manage_script()
{
    echo "./bin/module/manage.sh"
}

# Function to insert netio module, removing it first if present
# Usage: insert_netio_module [module_args...]
function insert_netio_module()
{
    local module_path=$(get_module_path)
    local manage_script=$(get_manage_script)

    # Check if module is already loaded
    if bash "$manage_script" check "$module_path" > /dev/null 2>&1; then
        echo "Removing existing module..."
        bash "$manage_script" rm "$module_path"
        if [ $? -ne 0 ]; then
            echo "Error: Failed to remove existing module"
            return 1
        fi
    fi

    # Insert the module with any provided arguments
    echo "Inserting module..."
    bash "$manage_script" add "$module_path" "$@"

    return $?
}

# Function to check if netio module is loaded
# Usage: is_netio_module_loaded
# Returns: 0 if loaded, 1 if not loaded
function is_netio_module_loaded()
{
    local module_path=$(get_module_path)
    local manage_script=$(get_manage_script)

    bash "$manage_script" check "$module_path"
    return $?
}

# Function to remove netio module
# Usage: remove_netio_module
function remove_netio_module()
{
    local module_path=$(get_module_path)
    local manage_script=$(get_manage_script)

    # Check if module is loaded
    if ! bash "$manage_script" check "$module_path" > /dev/null 2>&1; then
        echo "Module is not loaded"
        return 0
    fi

    # Remove the module
    echo "Removing module..."
    bash "$manage_script" rm "$module_path"

    return $?
}

# Function to get netio module syslog
# Usage: get_netio_module_syslog [syslog_file]
# Default syslog_file: /var/log/syslog
function get_netio_module_syslog()
{
    local module_path=$(get_module_path)
    local manage_script=$(get_manage_script)
    local syslog_file=${1:-/var/log/syslog}

    bash "$manage_script" log "$module_path" "$syslog_file"
    return $?
}

# Function to run activity test scripts (net, ns, unix)
# Usage: run_activity_scripts
function run_activity_scripts()
{
    local script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
    local activity_dir="${script_dir}/../activity"

    echo "=== Running Activity Test Scripts ==="

    # Run specific activity scripts in order
    local scripts=("net.sh" "ns.sh" "unix.sh")

    for script_name in "${scripts[@]}"; do
        local script="${activity_dir}/${script_name}"
        if [ -f "$script" ]; then
            echo "Running: $script"
            bash "$script"
            if [ $? -ne 0 ]; then
                echo "Warning: Script $script failed"
            fi
        else
            echo "Warning: Script $script not found"
        fi
    done

    echo "Activity scripts completed"
}

# Main test function that accepts a command and runs the full test workflow
# Usage: run_test_for_command "watch_audited_user"
function run_test_for_command()
{
    local command="$1"

    if [ -z "$command" ]; then
        echo "Error: command is required"
        echo "Usage: run_test_for_command <command>"
        return 1
    fi

    # Get script directories
    local script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
    local option_script="${script_dir}/option.sh"
    local auditd_script="${script_dir}/../../misc/auditd.sh"

    echo "========================================"
    echo "Running test for command: $command"
    echo "========================================"

    # Get options for the command
    echo ""
    echo "Step 1: Getting options for command..."
    local options=$(bash "$option_script" get_options_for_command "$command")
    if [ $? -ne 0 ]; then
        echo "Error: Failed to get options for command '$command'"
        return 1
    fi
    echo "Options: $options"

    # Stop auditd
    echo ""
    echo "Step 2: Stopping audit service..."
    bash "$auditd_script" stop
    if [ $? -ne 0 ]; then
        echo "Error: Failed to stop audit service"
        return 1
    fi

    # Start auditd
    echo ""
    echo "Step 3: Starting audit service..."
    bash "$auditd_script" start
    if [ $? -ne 0 ]; then
        echo "Error: Failed to start audit service"
        return 1
    fi

    # Clear audit rules
    echo ""
    echo "Step 4: Clearing audit rules..."
    bash "$auditd_script" clear
    if [ $? -ne 0 ]; then
        echo "Error: Failed to clear audit rules"
        return 1
    fi

    # Get current time
    echo ""
    echo "Step 5: Getting current time..."
    local start_time=$(bash "$auditd_script" time)
    echo "Start time: $start_time"

    # Insert netio module with options
    echo ""
    echo "Step 6: Inserting netio module with options..."
    insert_netio_module $options
    if [ $? -ne 0 ]; then
        echo "Error: Failed to insert netio module"
        return 1
    fi

    # Run activity scripts
    echo ""
    echo "Step 7: Running activity scripts..."
    run_activity_scripts

    # Ask user to continue
    echo ""
    echo "Step 8: Press Enter to continue (this allows audit events to be written)..."
    read -r

    # Remove netio module
    echo ""
    echo "Step 9: Removing netio module..."
    remove_netio_module
    if [ $? -ne 0 ]; then
        echo "Warning: Failed to remove netio module"
    fi

    # Clear audit rules again
    echo ""
    echo "Step 10: Clearing audit rules again..."
    bash "$auditd_script" clear
    if [ $? -ne 0 ]; then
        echo "Warning: Failed to clear audit rules"
    fi

    # Get audit logs after start time
    echo ""
    echo "Step 11: Getting audit logs after $start_time..."
    bash "$auditd_script" log "$start_time"

    echo ""
    echo "========================================"
    echo "Test completed for command: $command"
    echo "========================================"

    return 0
}

# Main function to handle command-line arguments
function main()
{
    local action="$1"
    shift  # Remove first argument, leaving the rest as $@

    case "$action" in
        test)
            run_test_for_command "$@"
            ;;
        insert)
            insert_netio_module "$@"
            ;;
        remove)
            remove_netio_module
            ;;
        check)
            is_netio_module_loaded
            ;;
        log)
            get_netio_module_syslog "$@"
            ;;
        activity)
            run_activity_scripts
            ;;
        *)
            echo "Usage: $0 {test|insert|remove|check|log|activity} [args]"
            echo ""
            echo "Commands:"
            echo "  test <command>     - Run full test workflow for command (e.g., 'watch_audited_user')"
            echo "  insert [options]   - Insert netio module with optional module parameters"
            echo "  remove             - Remove netio module"
            echo "  check              - Check if netio module is loaded"
            echo "  log [syslog_file]  - Get netio module syslog (default: /var/log/syslog)"
            echo "  activity           - Run activity test scripts"
            echo ""
            echo "Examples:"
            echo "  $0 test watch_audited_user"
            echo "  $0 insert net_io=1 namespaces=1"
            echo "  $0 remove"
            return 1
            ;;
    esac
}

# Execute main function with all arguments if script is run directly
if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    main "$@"
fi
