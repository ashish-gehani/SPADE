#!/bin/bash
# Stage B: run the container methods on a recorded trace, on PostgreSQL, Neo4j and Quickstep.
# The IDs to query come from the kernel's ground truth (truth.txt), not from the
# methods' own output. Extra queries (e.g. numbered forms) can be appended as
# arguments: query.sh 1 "getContainerBoundary('4026532353', 2)" ...
# Output: ~/stage-b/trace<N>/<storage>/{queries.txt,client.log,*.dot}
# Each command is timed (<storage>/times.tsv) and may take QUERY_TIMEOUT seconds
# (900 by default). STORAGES names the storages to query (all three by default).
# ONLY_EXTRA=1 runs only the extra calls, adding their results to an earlier run's
# (queries-extra.txt, client-extra.log, times-extra.tsv). A boundary call is named
# after its ID: id_<id>.dot, or id_<id>_<n>.dot for the numbered form.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)   # this directory
N=${1:?usage: query.sh 1|2|3 [extra method calls]}
shift
T="$HOME/stage-b/trace$N"
# the SPADE tree these scripts are in, unless SPADE_HOME names another
SPADE_HOME=${SPADE_HOME:-$(cd "$HERE/../../.." && pwd)}
cd "$SPADE_HOME" || exit 1

ids=$(grep -oE '\b[XY]=[0-9]+' "$T/truth.txt" | cut -d= -f2 | sort -u)

for storage in ${STORAGES:-PostgreSQL Neo4j Quickstep}; do
    D="$T/$storage"
    if [ "${ONLY_EXTRA:-0}" = 1 ]; then
        sfx=-extra; mkdir -p "$D"
    else
        sfx=; rm -rf "$D"; mkdir -p "$D"
    fi
    Q="$D/queries$sfx.txt"
    {
        echo "set storage $storage"
        echo "stat \$base"
        k=0
        add() {   # add NAME CALL: assign, count, export
            echo "\$$1 = \$base.$2"
            echo "stat \$$1"
            echo "export > $D/$1.dot"
            echo "dump all \$$1"   # into the file export named
        }
        if [ "${ONLY_EXTRA:-0}" != 1 ]; then
            add init "getContainerInit()"
            add all "getContainerBoundary()"
            for id in $ids; do add "id_$id" "getContainerBoundary('$id')"; done
        fi
        for call in "$@"; do
            name=$(echo "$call" | sed -nE "s/^getContainerBoundary\('([0-9]+)'(, *([0-9]+))?\)$/id_\1_\3/p" | sed 's/_$//')
            case "$call" in
                "getContainerInit()") name=init ;;
                "getContainerBoundary()") name=all ;;
            esac
            [ -n "$name" ] || { k=$((k + 1)); name="extra_$k"; }
            add "$name" "$call"
        done
        echo "exit"
    } > "$Q"
    echo "=== $storage: $(grep -c '^\$' "$Q") queries"
    s=$(date +%s)
    python3 "$HERE"/timed-client.py "$PWD" "$Q" "$D/client$sfx.log" "$D/times$sfx.tsv" "${QUERY_TIMEOUT:-900}"
    echo "client exit=$?, $(ls "$D"/*.dot 2>/dev/null | wc -l) graphs exported in $(( $(date +%s) - s )) s"
done
