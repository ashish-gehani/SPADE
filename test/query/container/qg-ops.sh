#!/bin/bash
# QuickGrail operations on each storage's $base, for comparing builds and storages:
# prints "storage | query | vertices edges" for every stat.
# Usage: qg-ops.sh OUT_FILE   (SPADE running, the storages holding the same graph)
set -u
OUT=${1:?usage: qg-ops.sh OUT_FILE}
OUT="$(cd "$(dirname "$OUT")" && pwd)/$(basename "$OUT")"
HERE=$(cd "$(dirname "$0")" && pwd)
# the SPADE tree these scripts are in, unless SPADE_HOME names another
cd "${SPADE_HOME:-$(cd "$HERE/../../.." && pwd)}" || exit 1
: > "$OUT"
for s in PostgreSQL Neo4j Quickstep; do
    printf '%s\n' "set storage $s" \
        "\$p = \$base.getVertex(type = 'Process')" "stat \$p" \
        "\$w = \$base.getEdge(operation = 'write')" "stat \$w" \
        "\$star = \$base.getEdge(* = 'write')" "stat \$star" \
        "\$ends = \$w.getEdgeEndpoints()" "stat \$ends" \
        "\$src = \$w.getEdgeSource()" "stat \$src" \
        "\$dst = \$w.getEdgeDestination()" "stat \$dst" \
        "\$u = \$p + \$ends" "stat \$u" \
        "\$i = \$p & \$ends" "stat \$i" \
        "\$d = \$ends - \$p" "stat \$d" \
        "\$runc = \$base.getVertex(name = 'runc')" "stat \$runc" \
        "\$sh = \$base.getVertex(name = 'sh')" "stat \$sh" \
        "\$desc = \$base.getLineage(\$runc, 2, 'd')" "stat \$desc" \
        "\$anc = \$base.getLineage(\$sh, 2, 'a')" "stat \$anc" \
        "\$sub = \$base.getSubgraph(\$p)" "stat \$sub" \
        "\$path = \$base.getPath(\$runc, \$sh, 3)" "stat \$path" \
        "\$c = \$w.collapseEdge('operation')" "stat \$c" \
        "\$m = \$p.getMatch(\$runc, 'name')" "stat \$m" \
        "\$vs = \$p.vertexSubset(0, 100)" "stat \$vs" \
        "\$es = \$w.edgeSubset(0, 100)" "stat \$es" \
        "stat \$base" "exit" \
        | timeout 3600 bin/spade query 2>&1 \
        | awk -F'|' -v s="$s" '
            /^-> stat /{q=substr($0, 4); next}
            /^-> /{q=""; next}
            /^Error/{print s " | " (q ? q : "?") " | " $0}
            q && /^\| [0-9]+ +\| [0-9]+/{gsub(/ /, "", $2); gsub(/ /, "", $3); print s " | " q " | " $2 " " $3; q=""}' \
        >> "$OUT"
done
git checkout -q -- cfg/spade.client.CommandLine.config
cat "$OUT"
