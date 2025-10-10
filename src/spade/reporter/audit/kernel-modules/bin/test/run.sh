#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR=$(dirname "${BASH_SOURCE[0]}")
BIN_DIR="$SCRIPT_DIR/.."
MOD_NAME=spade_audit_test
MODULE_PATH="$SCRIPT_DIR/../../build/${MOD_NAME}.ko"

# Function to insert test module
function insert_test_module()
{
    local module_args="$@"

    echo "=== Inserting test module ==="

    if [ ! -f "$MODULE_PATH" ]; then
        echo "Error: Test module not found at $MODULE_PATH"
        echo "Please build the module first with 'make'"
        return 1
    fi

    # Check if module is already loaded and remove it
    if lsmod | grep -q "^spade_audit_test "; then
        echo "Module already loaded, removing it first..."
        remove_test_module
        if [ $? -ne 0 ]; then
            echo "Warning: Failed to remove existing module"
        fi
    fi

    # Use manage_module.sh to insert the module
    bash "$BIN_DIR/manage_module.sh" insmod "$MODULE_PATH" $module_args

    return $?
}

# Function to remove test module
function remove_test_module()
{
    echo "=== Removing test module ==="

    # Use manage_module.sh to remove the module
    bash "$BIN_DIR/manage_module.sh" rmmod spade_audit_test

    return $?
}

# Function to get module test results from kernel log
function get_test_results()
{
    echo "=== Test Results ==="

    # Use read_module_syslog.sh to get module logs and filter for test result lines
    bash "$BIN_DIR/read_module_syslog.sh" "${MOD_NAME}" | grep -E '\[spade_audit_test\].*Tests:.*total.*passed.*failed'

    return $?
}

# Function to run activity test scripts
function run_activity_tests()
{
    echo "=== Running Activity Tests ==="

    local test_scripts=(
        "$SCRIPT_DIR/activity_net.sh"
        "$SCRIPT_DIR/activity_unix.sh"
        "$SCRIPT_DIR/activity_ns.sh"
    )

    local failed=0

    for script in "${test_scripts[@]}"; do
        if [ -f "$script" ]; then
            echo "Running $(basename $script)..."
            bash "$script"
            if [ $? -ne 0 ]; then
                echo "Warning: $(basename $script) failed"
                ((failed++))
            fi
        else
            echo "Warning: $(basename $script) not found"
            ((failed++))
        fi
    done

    if [ $failed -eq 0 ]; then
        echo "All activity tests completed successfully"
        return 0
    else
        echo "$failed activity test(s) failed"
        return 1
    fi
}

# Function to run complete test suite
function run_full_test()
{
    local module_args="$@"

    echo "========================================"
    echo "Running Full Test Suite"
    echo "========================================"

    # Insert the module
    insert_test_module $module_args
    if [ $? -ne 0 ]; then
        echo "Error: Failed to insert test module"
        return 1
    fi

    # Run activity tests
    run_activity_tests
    local activity_result=$?

    # Remove the module
    remove_test_module
    if [ $? -ne 0 ]; then
        echo "Warning: Failed to remove test module"
    fi

    # Get and display test results
    get_test_results

    echo "========================================"
    if [ $activity_result -eq 0 ]; then
        echo "Full test suite completed successfully"
        return 0
    else
        echo "Full test suite completed with failures"
        return 1
    fi
}

# Main execution
run_full_test "$@"