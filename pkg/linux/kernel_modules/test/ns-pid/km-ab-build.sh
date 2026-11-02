#!/bin/bash
# Build the audit kernel module twice for the ns pid A/B, each from a pristine
# git archive of its commit, so the builds cannot affect each other.
# Usage: km-ab-build.sh BASE_REF FIXED_REF        e.g. km-ab-build.sh 6b744f7c d445c7cc
# Output: $KM_ROOT/{base,fixed}/pkg/linux/kernel_modules/build/*.ko  (KM_ROOT defaults to ~/km-ab)
set -u
BASE_REF=${1:?usage: km-ab-build.sh BASE_REF FIXED_REF}
FIXED_REF=${2:?usage: km-ab-build.sh BASE_REF FIXED_REF}
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(git -C "$HERE" rev-parse --show-toplevel) || exit 1
KDIR="/usr/src/linux-headers-$(uname -r)"
OUT="${KM_ROOT:-$HOME/km-ab}"

[ -d "$KDIR" ] || { echo "ERROR: no headers for the running kernel at $KDIR"; exit 1; }
echo "kernel: $(uname -r)"
echo "=== module source differences, base -> fixed:"
git -C "$REPO" diff --stat "$BASE_REF" "$FIXED_REF" -- pkg/linux/kernel_modules

for v in "base:$BASE_REF" "fixed:$FIXED_REF"; do
    name=${v%%:*}
    ref=${v#*:}
    dir="$OUT/$name"
    rm -rf "$dir"
    mkdir -p "$dir"
    git -C "$REPO" archive "$ref" pkg/linux/kernel_modules | tar -x -C "$dir"
    echo
    echo "=== $name: $ref = $(git -C "$REPO" rev-parse --short "$ref")"
    ( cd "$dir/pkg/linux/kernel_modules" && \
      make -j"$(nproc)" -f Kbuild PACKAGE_DIR="$dir/out" KDIR="$KDIR" all ) > "$dir/make.log" 2>&1
    echo "make exit=$?  (log: $dir/make.log)"
    ls -l "$dir"/pkg/linux/kernel_modules/build/*.ko 2>/dev/null | awk '{print "  " $5 " bytes  " $9}'
    echo "  errors:   $(grep -cE 'error:' "$dir/make.log")"
    echo "  warnings: $(grep -E 'warning:' "$dir/make.log" | sed 's/.*warning: //' | sort -u | tr '\n' '|')"
done
