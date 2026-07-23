#!/bin/bash

# Global module name constants
readonly MAIN_MOD_NAME="spade_audit"
readonly CONTROLLER_MOD_NAME="spade_audit_controller"

# Function to get the main module path
# Usage: get_module_path
function get_module_path()
{
    echo "./build/${MAIN_MOD_NAME}.ko"
}

# Function to get the manage script path
# Usage: get_manage_script
function get_manage_script()
{
    echo "./bin/module/manage.sh"
}

# Function to insert main module, removing it first if present
# Usage: insert_spade_audit_module [module_args...]
function insert_spade_audit_module()
{
    local module_path=$(get_module_path)
    local manage_script=$(get_manage_script)

    # Check if module is already loaded
    if bash "$manage_script" check "$module_path" > /dev/null 2>&1; then
        echo "Removing existing ${MAIN_MOD_NAME} module..."
        bash "$manage_script" rm "$module_path"
        if [ $? -ne 0 ]; then
            echo "Error: Failed to remove existing ${MAIN_MOD_NAME} module"
            return 1
        fi
    fi

    # Insert the module with any provided arguments
    echo "Inserting ${MAIN_MOD_NAME} module..."
    bash "$manage_script" add "$module_path" "$@"

    return $?
}

# Function to check if main module is loaded
# Usage: is_spade_audit_module_loaded
# Returns: 0 if loaded, 1 if not loaded
function is_spade_audit_module_loaded()
{
    local module_path=$(get_module_path)
    local manage_script=$(get_manage_script)

    bash "$manage_script" check "$module_path"
    return $?
}

# Function to remove main module
# Usage: remove_spade_audit_module
function remove_spade_audit_module()
{
    local module_path=$(get_module_path)
    local manage_script=$(get_manage_script)

    # Check if module is loaded
    if ! bash "$manage_script" check "$module_path" > /dev/null 2>&1; then
        echo "${MAIN_MOD_NAME} module is not loaded"
        return 0
    fi

    # Remove the module
    echo "Removing ${MAIN_MOD_NAME} module..."
    bash "$manage_script" rm "$module_path"

    return $?
}

# Function to get main module syslog
# Usage: get_spade_audit_module_syslog [syslog_file]
# Default syslog_file: /var/log/syslog
function get_spade_audit_module_syslog()
{
    local syslog_file=${1:-/var/log/syslog}
    local syslog_script="./bin/module/syslog.sh"

    bash "$syslog_script" "${MAIN_MOD_NAME}" "$syslog_file"
    return $?
}

# Function to get the controller module path
# Usage: get_spade_audit_controller_module_path
function get_spade_audit_controller_module_path()
{
    echo "./build/${CONTROLLER_MOD_NAME}.ko"
}

# Function to insert controller module, removing it first if present
# Usage: insert_spade_audit_controller_module [module_args...]
function insert_spade_audit_controller_module()
{
    local module_path=$(get_spade_audit_controller_module_path)
    local manage_script=$(get_manage_script)

    # Check if module is already loaded
    if bash "$manage_script" check "$module_path" > /dev/null 2>&1; then
        echo "Removing existing ${CONTROLLER_MOD_NAME} module..."
        bash "$manage_script" rm "$module_path"
        if [ $? -ne 0 ]; then
            echo "Error: Failed to remove existing ${CONTROLLER_MOD_NAME} module"
            return 1
        fi
    fi

    # Insert the module with any provided arguments
    echo "Inserting ${CONTROLLER_MOD_NAME} module..."
    bash "$manage_script" add "$module_path" "$@"

    return $?
}

# Function to check if controller module is loaded
# Usage: is_spade_audit_controller_module_loaded
# Returns: 0 if loaded, 1 if not loaded
function is_spade_audit_controller_module_loaded()
{
    local module_path=$(get_spade_audit_controller_module_path)
    local manage_script=$(get_manage_script)

    bash "$manage_script" check "$module_path"
    return $?
}

# Function to remove controller module
# Usage: remove_spade_audit_controller_module
function remove_spade_audit_controller_module()
{
    local module_path=$(get_spade_audit_controller_module_path)
    local manage_script=$(get_manage_script)

    # Check if module is loaded
    if ! bash "$manage_script" check "$module_path" > /dev/null 2>&1; then
        echo "${CONTROLLER_MOD_NAME} module is not loaded"
        return 0
    fi

    # Remove the module
    echo "Removing ${CONTROLLER_MOD_NAME} module..."
    bash "$manage_script" rm "$module_path"

    return $?
}

# Function to get controller module syslog
# Usage: get_spade_audit_controller_module_syslog [syslog_file]
# Default syslog_file: /var/log/syslog
function get_spade_audit_controller_module_syslog()
{
    local syslog_file=${1:-/var/log/syslog}
    local syslog_script="./bin/module/syslog.sh"

    bash "$syslog_script" "${CONTROLLER_MOD_NAME}" "$syslog_file"
    return $?
}

# Function to get the next run number for output directory
# Usage: get_next_run_number
function get_next_run_number()
{
    local output_base_dir="./output/test/spade_audit/run"

    # Create base directory if it doesn't exist
    mkdir -p "$output_base_dir"

    # Find the highest existing run number
    local max_num=0
    if [ -d "$output_base_dir" ]; then
        for dir in "$output_base_dir"/*/; do
            if [ -d "$dir" ]; then
                local num=$(basename "$dir")
                if [[ "$num" =~ ^[0-9]+$ ]] && [ "$num" -gt "$max_num" ]; then
                    max_num=$num
                fi
            fi
        done
    fi

    # Return next number
    echo $((max_num + 1))
}

# Function to create and return the run output directory
# Usage: create_run_output_dir
# Returns: The path to the created run directory
function create_run_output_dir()
{
    local run_number=$(get_next_run_number)
    local run_dir="./output/test/spade_audit/run/$run_number"

    mkdir -p "$run_dir"
    echo "$run_dir"
}

# Function to run activity test scripts (net, ns, unix)
# Usage: run_activity_scripts
function run_activity_scripts()
{
    local script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
    local activity_dir="${script_dir}/../activity"

    echo "=== Running Activity Test Scripts ==="

    # Run specific activity scripts in order
    local scripts=(
        "net.sh"
        "ns.sh"
        "ubsi.sh"
        "unix.sh"
        "kill_harden.sh"
    )

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
    local harden_dummy_script="${script_dir}/dummy_process_for_hardening.sh"

    echo "========================================"
    echo "Running test for command: $command"
    echo "========================================"

    # Create output directory for this run
    local output_dir=$(create_run_output_dir)
    echo "Output directory: $output_dir"
    echo ""

    # Start harden dummy process
    echo "Step 0.5: Starting harden dummy process..."
    if [ -f "$harden_dummy_script" ]; then
        bash "$harden_dummy_script" start
        if [ $? -ne 0 ]; then
            echo "Warning: Failed to start harden dummy process"
        fi
    else
        echo "Warning: Harden dummy process script not found at $harden_dummy_script"
    fi

    sleep 2

    # Get options for the command
    echo ""
    echo "Step 1: Getting options for command..."
    local options=$(bash "$option_script" "$command")
    if [ $? -ne 0 ]; then
        echo "Error: Failed to get options for command '$command'"
        return 1
    fi
    echo "Options: $options"

    sleep 5

    # Stop auditd
    echo ""
    echo "Step 2: Stopping audit service..."
    bash "$auditd_script" stop
    if [ $? -ne 0 ]; then
        echo "Error: Failed to stop audit service"
        return 1
    fi

    sleep 5

    # Start auditd
    echo ""
    echo "Step 3: Starting audit service..."
    bash "$auditd_script" start
    if [ $? -ne 0 ]; then
        echo "Error: Failed to start audit service"
        return 1
    fi

    sleep 5

    # Clear audit rules
    echo ""
    echo "Step 4: Clearing audit rules..."
    bash "$auditd_script" clear
    if [ $? -ne 0 ]; then
        echo "Error: Failed to clear audit rules"
        return 1
    fi

    sleep 5

    # Get current time
    echo ""
    echo "Step 5: Getting current time..."
    local start_time=$(bash "$auditd_script" time)
    echo "Start time: $start_time"

    sleep 5

    # Insert main module
    echo ""
    echo "Step 6: Inserting ${MAIN_MOD_NAME} module..."
    insert_spade_audit_module
    if [ $? -ne 0 ]; then
        echo "Error: Failed to insert ${MAIN_MOD_NAME} module"
        return 1
    fi

    sleep 5

    # Insert controller module with options
    echo ""
    echo "Step 7: Inserting ${CONTROLLER_MOD_NAME} module with options..."
    insert_spade_audit_controller_module $options
    if [ $? -ne 0 ]; then
        echo "Error: Failed to insert ${CONTROLLER_MOD_NAME} module"
        return 1
    fi

    sleep 5

    # Run activity scripts
    echo ""
    echo "Step 8: Running activity scripts..."
    run_activity_scripts

    # Ask user to continue
    echo ""
    # echo "Step 9: Press Enter to continue (this allows audit events to be written)..."
    # read -r
    echo "Step 9: Sleeping for 15 seconds..."
    sleep 15

    # Remove controller module
    echo ""
    echo "Step 10: Removing ${CONTROLLER_MOD_NAME} module..."
    remove_spade_audit_controller_module
    if [ $? -ne 0 ]; then
        echo "Warning: Failed to remove ${CONTROLLER_MOD_NAME} module"
    fi

    sleep 5

    # Remove main module
    echo ""
    echo "Step 11: Removing ${MAIN_MOD_NAME} module..."
    remove_spade_audit_module
    if [ $? -ne 0 ]; then
        echo "Warning: Failed to remove ${MAIN_MOD_NAME} module"
    fi

    sleep 5

    # Clear audit rules again
    echo ""
    echo "Step 12: Clearing audit rules again..."
    bash "$auditd_script" clear
    if [ $? -ne 0 ]; then
        echo "Warning: Failed to clear audit rules"
    fi

    sleep 5

    # Get audit logs after start time
    echo ""
    echo "Step 13: Getting audit logs after $start_time..."
    local audit_log_file="$output_dir/audit.log"
    bash "$auditd_script" log "$start_time" > "$audit_log_file"
    echo "Audit log saved to: $audit_log_file"

    sleep 2

    # Get main module syslog
    echo ""
    echo "Step 14: Getting ${MAIN_MOD_NAME} module syslog..."
    local spade_audit_syslog_file="$output_dir/${MAIN_MOD_NAME}.syslog"
    get_spade_audit_module_syslog > "$spade_audit_syslog_file"
    echo "${MAIN_MOD_NAME} syslog saved to: $spade_audit_syslog_file"

    sleep 2

    # Get controller module syslog
    echo ""
    echo "Step 15: Getting ${CONTROLLER_MOD_NAME} module syslog..."
    local spade_audit_controller_syslog_file="$output_dir/${CONTROLLER_MOD_NAME}.syslog"
    get_spade_audit_controller_module_syslog > "$spade_audit_controller_syslog_file"
    echo "${CONTROLLER_MOD_NAME} syslog saved to: $spade_audit_controller_syslog_file"

    # Kill harden dummy process
    echo ""
    echo "Step 16: Killing harden dummy process..."
    if [ -f "$harden_dummy_script" ]; then
        bash "$harden_dummy_script" kill
        if [ $? -ne 0 ]; then
            echo "Warning: Failed to kill harden dummy process"
        fi
    fi

    echo ""
    echo "========================================"
    echo "Test completed for command: $command"
    echo "All logs saved to: $output_dir"
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
            insert_spade_audit_module "$@"
            ;;
        remove)
            remove_spade_audit_module
            ;;
        check)
            is_spade_audit_module_loaded
            ;;
        log)
            get_spade_audit_module_syslog "$@"
            ;;
        insert-controller)
            insert_spade_audit_controller_module "$@"
            ;;
        remove-controller)
            remove_spade_audit_controller_module
            ;;
        check-controller)
            is_spade_audit_controller_module_loaded
            ;;
        log-controller)
            get_spade_audit_controller_module_syslog "$@"
            ;;
        activity)
            run_activity_scripts
            ;;
        *)
            echo "Usage: $0 {test|insert|remove|check|log|insert-controller|remove-controller|check-controller|log-controller|activity} [args]"
            echo ""
            echo "Module Configuration:"
            echo "  Main module: ${MAIN_MOD_NAME}"
            echo "  Controller module: ${CONTROLLER_MOD_NAME}"
            echo ""
            echo "Commands:"
            echo "  test <command>              - Run full test workflow for command (e.g., 'watch_audited_user')"
            echo "  insert [options]            - Insert ${MAIN_MOD_NAME} module (no options)"
            echo "  remove                      - Remove ${MAIN_MOD_NAME} module"
            echo "  check                       - Check if ${MAIN_MOD_NAME} module is loaded"
            echo "  log [syslog_file]           - Get ${MAIN_MOD_NAME} module syslog (default: /var/log/syslog)"
            echo "  insert-controller [options] - Insert ${CONTROLLER_MOD_NAME} module with optional module parameters"
            echo "  remove-controller           - Remove ${CONTROLLER_MOD_NAME} module"
            echo "  check-controller            - Check if ${CONTROLLER_MOD_NAME} module is loaded"
            echo "  log-controller [syslog_file]- Get ${CONTROLLER_MOD_NAME} module syslog (default: /var/log/syslog)"
            echo "  activity                    - Run activity test scripts"
            echo ""
            echo "Examples:"
            echo "  $0 test watch_audited_user"
            echo "  $0 insert"
            echo "  $0 insert-controller net_io=1 namespaces=1"
            echo "  $0 remove-controller"
            echo "  $0 remove"
            return 1
            ;;
    esac
}

# Execute main function with all arguments if script is run directly
if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    main "$@"
fi
