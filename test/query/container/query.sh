#!/bin/bash
# Run the ContainerLabels transformer on a recorded trace, on PostgreSQL, Neo4j and
# Quickstep, and export what the checkers compare with the kernel's ground truth:
#   containers.dot  the vertices the transformer labeled (container_id)
#   processes.dot   every process vertex of the labeled copy, labeled or not
#   sockets.dot     every network socket and process of the labeled copy, with the
#                   edges between them (getSubgraph: getLineage on sockets would try
#                   a remote SPADE server for each one)
# Output: ~/stage-b/trace<N>/<storage>/{queries.txt,client.log,times.tsv,*.dot}
# Each command is timed (times.tsv) and may take QUERY_TIMEOUT seconds (900 by
# default). STORAGES names the storages to query (all three by default).
# The transform puts its labeled copy into the storage (QueryInstructionExecutor.
# transformGraph), so $base grows by the labeled vertices and their edges.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)   # this directory
N=${1:?usage: query.sh 1|2|3|4|<name>}
T="$HOME/stage-b/trace$N"
# the SPADE tree these scripts are in, unless SPADE_HOME names another
SPADE_HOME=${SPADE_HOME:-$(cd "$HERE/../../.." && pwd)}
cd "$SPADE_HOME" || exit 1

for storage in ${STORAGES:-PostgreSQL Neo4j Quickstep}; do
    D="$T/$storage"
    rm -rf "$D"
    # A storage SPADE does not have would leave the client on its last storage.
    if ! printf 'set storage %s\nexit\n' "$storage" | timeout 300 bin/spade query 2>&1 \
            | grep -q "Storage '$storage' successfully set"; then
        echo "=== $storage: not added to SPADE, skipped"; continue
    fi
    mkdir -p "$D"
    Q="$D/queries.txt"
    cat > "$Q" <<EOF
set storage $storage
stat \$base
\$labeled = \$base.transform(ContainerLabels, '')
stat \$labeled
stat \$base
\$containers = \$labeled.getVertex(container_id like '%')
stat \$containers
export > $D/containers.dot
dump all \$containers
\$processes = \$labeled.getVertex(type = 'Process')
stat \$processes
export > $D/processes.dot
dump all \$processes
\$sockets = \$labeled.getVertex(subtype = 'network socket')
\$skeleton = \$sockets + \$processes
\$socketsAround = \$labeled.getSubgraph(\$skeleton)
stat \$socketsAround
export > $D/sockets.dot
dump all \$socketsAround
exit
EOF
    echo "=== $storage: $(grep -c '^\$' "$Q") queries"
    s=$(date +%s)
    python3 "$HERE"/timed-client.py "$PWD" "$Q" "$D/client.log" "$D/times.tsv" "${QUERY_TIMEOUT:-900}"
    echo "client exit=$?, $(ls "$D"/*.dot 2>/dev/null | wc -l) graphs exported in $(( $(date +%s) - s )) s"
done
git checkout -q -- cfg/spade.client.CommandLine.config 2>/dev/null   # the client saved its last storage
