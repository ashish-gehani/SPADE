# QuickGrail Reference

## Basic Examples
```
# Define a constraint to match all vertices with IPs that start with '128.55.12'
%start_ip_constraint = * like '128.55.12.%'
# Find vertices of interest using the constraint defined above
$start_ip = $base.getVertex(%start_ip_constraint);
# Find vertices of interest using the constraint directly
$elevate_me = $base.getVertex(* like '%elevateme%');

# Find paths from $elevate_me to $start_ip of length at most 4
$paths = $base.getPath($elevate_me, $start_ip, 4);

# Print subgraph in query client
dump $paths;
# Output subgraph to file
export > /tmp/paths.dot
visualize force $paths
```

## Lexical Structure
* Integer Literals
  * e.g. `1234`
* String Literals
  * e.g. `'abc'`, `'%127.0.0.1%'`
* Name
  * e.g. `dump`, `type`, `*` (_yes "star" is a name_..), `"a name with space and double quotes"`
* Operators
  * e.g. `=`, `+`, `+=`, `-`, `-=`, `&`, `&=`
* Graph Variables
  * Variable name format: `$[a-zA-Z0-9_]+`
    * e.g. `$ip`, `$1`
    * `$base` is a special immutable variable that represents the whole graph
* Constraint Variables
  * Variable name format: `%[a-zA-Z0-9_]+`
    * e.g. `%ip`, `%1`

## Expressions
* Graph Expressions
  * _graph_ ::= _graph-variable_
  * _graph_ ::= _graph_ `.` _graph-method_ `(` _argument-list_ `)`
  * _graph_ ::= _graph_ ( `+` | `-` | `&` ) _graph_
* Constraint Expressions
  * _constraint-name_ ::= %[a-zA-Z0-9_]+
  * _constraint-comparison-expression_ ::= _name_ ( `==` | `!=` | `>` | `<` | `>=` | `<=` | `like` ) _string-literal_
  * _constraint-expression_ ::= [ `not` ] _constraint-comparison-expression_ | [ `not` ] _constraint-name_
  * _constraint_ ::= _constraint-expression_ [ `and` | `or` _constraint-expression_ ]

## Commands
* List existing graphs, constraints, and environment variables
  * `list` to see all
  * `list graph` for variables bound to graphs
  * `list constraint` for constraints that have been defined
  * `list env` for current enviroment variables
* Set, unset, and print environment variables
  * `env set` _variable_name_ _integer_
  * `env unset` _variable_name_
  * `env print` _variable_name_
* Print graph statistics
  * `stat` _graph_
* Print graph as a SPADE `Graph`
  * `dump` _graph_
* Print constraint
  * `dump` _constraint_
* Print graph in DOT format
  * `visualize` [`force`] _graph_
  * using `force` prints entire graph, even if it exceeds limit in `cfg/spade.query.quickgrail.QuickGrailExecutor.config`
* Remove a list of variables (_graph_ or _constraint_)
  * `erase` (_graph_)+
* Remove all variables
  * `reset workspace`
* Execute query directly in the underlying storage's language
  * `native` '_query_in_single_quotes_'

## Methods
#### Method Declaration Notation

_return-type_ **method-name** ( **_argument-type_** formal-argument, ... )

---

#### Graph Methods
* _graph_ **getVertex** ( )
  * Get all the vertices
    * e.g. `$2 = $1.getVertex()`
* _graph_ **getVertex** ( **_constraint_** constraint )
  * Get all vertices that match a constraint
    * e.g. `$2 = $1.getVertex(* LIKE '%firefox%')`
* _graph_ **getMatch** ( **_graph_** otherGraph, **_string_** annotation, ... )
  * Get vertices in operand and `otherGraph` that have all `annotation` keys specified and the values of those keys match
    * e.g. `$3 = $1.getMatch($2, 'pid', 'ppid')` returns all vertices in `$1` or `$2` if a vertex in the other graph had the same values for annotation keys `pid` and `ppid`
  * _NOTE_: Experimental
* _graph_ **getEdge** ( )
  * Get all the edges
    * e.g. `$2 = $1.getEdge()`
* _graph_ **getEdge** ( **_constraint_** constraint )
  * Get all the edges that match a constraint
    * e.g. `$2 = $1.getEdge(operation = 'write')`
* _graph_ **collapseEdge** ( **_string_** annotation, ... )
  * Collapse edges with regard to the annotations
    * e.g. `$2 = $1.collapseEdge('type', 'operation')`
* _graph_ **getEdgeEndpoints** ( )
  * Get all the vertices that are endpoints of edges
    * e.g. `$2 = $1.getEdgeEndpoints()`
* _graph_ **getEdgeSource** ( )
  * Get all the vertices that are source endpoints of edges
    * e.g. `$2 = $1.getEdgeSource()`
* _graph_ **getEdgeDestination** ( )
  * Get all the vertices that are destination endpoints of edges
    * e.g. `$2 = $1.getEdgeDestination()`
* _graph_ **getLineage** ( **_graph_** sourceVertices [ , **_int_** maxDepth ] , **_string_** direction )
  * Get lineage of a set of source vertices
  * _direction_ can be `'ancestor'`(or `'a'`) / `'descendant'` (or `'d'`) / `'both'` (or `'b'`).
    * e.g. `$2 = $base.getLineage($1, 3, 'b')` or `$2 = $base.getLineage($1, 'b')` to implicitly use `maxDepth` environment variable
* _graph_ **getPath** ( **_graph_** sourceVertices, ( **_graph_** destinationVertices [ , **_int_** maxDepth ] )+ )
  * Get paths from a set of source vertices to a set of destination vertices, restricted to those that pass through specified intermediate vertices
    * e.g. `$3 = $base.getPath($1, $2, 5)`
    * e.g. `$4 = $base.getPath($1, $2, 9, $3)` with a maximum path length between `$1` and `$2` of 9, and maximum path length between `$2` and `$3` implicitly defined by `maxDepth` environment variable
* _graph_ **getShortestPath** ( **_graph_** sourceVertices, **_graph_** destinationVertices[, **_int_** maxDepth] )
  * Get the shortest path from a set of source vertices to a set of destination vertices
  * _NOTE_: Currently this finds a short path, but not the shortest path
    * e.g. `$3 = $somePath.getShortestPath($1, $2, 5)` or `$3 = $somePath.getShortestPath($1, $2)` to implicitly use `maxDepth` environment variable
* _graph_ **getSubgraph** ( **_graph_** skeletonGraph )
  * Get all vertices and edges that are spanned by `skeletonGraph`
    * e.g. `$2 = $base.getSubgraph($1)`
* _graph_ **limit** ( [**_int_** limit] )
  * Get first (ordered by id) _limit_ vertices / edges
    * e.g. `$2 = $1.limit(10)` or `$2 = $1.limit()` to implicitly use `limit` environment variable
---
#### Functions
* _graph_ **vertices** ( **_string_** vertexHash, ... )
  * Get all vertices specified by their hashes
    * e.g. `$1 = vertices('815fd285f16cce9ab398b5b2ce5d2d03', '087b22992d4d871dc8c9ccd837132c6a')`
* _graph_ **edges** ( **_string_** edgeHash, ... )
  * Get all edges specified by their hashes
    * e.g. `$1 = edges('b161b03a4365faf44d8cdd3713f811e9', 'b161b03a4365faf44d8cdd3713f811e0')`

## Operators
* Graph Union `+`, `+=`
  * E.g. `$3 = $1 + $2`
  * E.g. `$b += $a`
* Graph Intersection `&`, `&=`
  * E.g. `$3 = $1 & $2`
  * E.g. `$b &= $a`
* Graph Subtract `-`, `-=`
  * E.g. `$3 = $1 - $2`
  * E.g. `$b -= $a`

