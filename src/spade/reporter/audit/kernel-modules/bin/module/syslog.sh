#!/bin/bash

# Script to extract module syslog messages based on instance_id
# Usage: ./module/syslog.sh [module_name] [syslog_file]

MODULE_NAME=${1:-netio}
SYSLOG_FILE=${2:-/var/log/syslog}

DEBUG=0

if [ ! -f "$SYSLOG_FILE" ]; then
    echo "Error: Syslog file not found: $SYSLOG_FILE"
    exit 1
fi

[ "$DEBUG" -eq 1 ] && \
    echo "=== Searching for module: $MODULE_NAME in $SYSLOG_FILE ==="

# Step 1: Find messages with pattern '[<module_name>] [spade:module:state:*] : [instance_id=<arbitrary string>]'
INSTANCE_IDS=$(grep -oP "\[$MODULE_NAME\] \[spade:module:state:[^\]]+\] : \[instance_id=\K[^\]]+" "$SYSLOG_FILE" | sort -u)

if [ -z "$INSTANCE_IDS" ]; then
    echo "No instance_id found for module: $MODULE_NAME"
    exit 0
fi

[ $DEBUG -eq 1 ] && \
    echo "Found instance IDs:" \
    echo "$INSTANCE_IDS" \
    echo ""

# Select the last instance_id
INSTANCE_ID=$(echo "$INSTANCE_IDS" | tail -1)

[ $DEBUG -eq 1 ] && \
    echo "Using last instance_id: $INSTANCE_ID" \
    echo ""

# Process the instance_id
if [ -n "$INSTANCE_ID" ]; then
    [ $DEBUG -eq 1 ] && \
        echo "========================================" \
        echo "Processing instance_id: $INSTANCE_ID" \
        echo "========================================"

    # Step 2: Find all messages with this instance_id
    MATCHING_LINES=$(grep -a -n "instance_id=$INSTANCE_ID" "$SYSLOG_FILE" | cut -d: -f1)

    if [ -z "$MATCHING_LINES" ]; then
        echo "No messages found for instance_id: $INSTANCE_ID"
    else

        # Get first and last line numbers
        FIRST_LINE=$(echo "$MATCHING_LINES" | head -1)
        LAST_LINE=$(echo "$MATCHING_LINES" | tail -1)

        [ $DEBUG -eq 1 ] && \
            echo "First occurrence: line $FIRST_LINE" \
            echo "Last occurrence: line $LAST_LINE" \
            echo ""

        # Step 3: Get all messages between first and last line where module_name matches
        [ $DEBUG -eq 1 ] && \
            echo "--- Messages for instance_id=$INSTANCE_ID ---"
        sed -n "${FIRST_LINE},${LAST_LINE}p" "$SYSLOG_FILE" | grep "\[$MODULE_NAME\]"
        [ $DEBUG -eq 1 ] && \
            echo ""

    fi
fi

[ $DEBUG -eq 1 ] && \
    echo "=== Done ==="
