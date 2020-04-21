# QuickGrail Reference

## Basic Examples
```
# Define a constraint to match all vertices with IPs that start with '128.55.12'
%start_ip_constraint = * like '128.55.12.%'
# Find the nodes that we are interested in by using the constraint defined above
$start_ip = $base.getVertex(%start_ip_constraint);
# Find the nodes that we are interested in by using the constraint directly
$elevate_me = $base.getVertex(* like '%elevateme%');

# Find the paths from $elevate_me to $start_ip of length at max 4
$paths = $base.getPath($elevate_me, $start_ip, 4);

# Output the subgraph to the client terminal
dump $paths;
# Output the subgraph to a file
export > /tmp/paths.dot
visualize force $paths
```

## Lexical Structure
* Integer Literals
  * E.g. `1234`
* String Literals
  * E.g. `'abc'`, `'%127.0.0.1%'`
* Name
  * E.g. `dump`, `type`, `*` (_yes "star" is a name_..), `"a name with space and double quotes"`
* Operators
  * E.g. `=`, `+`, `+=`, `-`, `-=`, `&`, `&=`
* Graph Variables
  * Variable name format: `$[a-zA-Z0-9_]+`
  * E.g. `$ip`, `$1`
    * `$base` is a special immutable variable that represents the overall graph.
* Constraint Variables
  * Variable name format: `%[a-zA-Z0-9_]+`
  * E.g. `%ip`, `%1`

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
* List all existing graphs and all existing constraints.
  * `list`
  * `list graph` will only list graphs
  * `list constraint` will only list constraints
* Output graph statistics.
  * `stat` _graph_
* Output graph as a SPADE `Graph`.
  * `dump` _graph_
* Output constraint.
  * `dump` _constraint_
* Output graph in DOT format.
  * `visualize` [`force`] _graph_
* Remove a list of variables (_graph_ or _constraint_).
  * `erase` (_graph_)+
* Remove all variables.
  * `reset workspace`
* Execute query directly on the underlying storage.
  * `native` '_query_in_single_quotes_'

## Methods
#### Method Declaration Notation

_return-type_ **method-name** ( **_argument-type_** formal-argument, ... )

---

#### Graph Methods
* _graph_ **getVertex** ( )
  * Get all the vertices.
  * E.g. `$2 = $1.getVertex()`
* _graph_ **getVertex** ( **_constraint_** constraint )
  * Get all the vertices that match a constraint.
  * E.g. `$2 = $1.getVertex(* LIKE '%firefox%')`
* _graph_ **getEdge** ( )
  * Get all the edges.
  * E.g. `$2 = $1.getEdge()`
* _graph_ **getEdge** ( **_constraint_** constraint )
  * Get all the edges that match a constraint.
  * E.g. `$2 = $1.getEdge(operation = 'write')`
* _graph_ **collapseEdge** ( **_string_** annotation, ... )
  * Collapse edges with regard to the annotations.
  * E.g. `$2 = $1.collapseEdge('type', 'operation')`
* _graph_ **getEdgeEndpoints** ( )
  * Get all the vertices that are endpoints of edges.
  * E.g. `$2 = $1.getEdgeEndpoints()`
* _graph_ **getEdgeSource** ( )
  * Get all the vertices that are source endpoints of edges.
  * E.g. `$2 = $1.getEdgeSource()`
* _graph_ **getEdgeDestination** ( )
  * Get all the vertices that are destination endpoints of edges.
  * E.g. `$2 = $1.getEdgeDestination()`
* _graph_ **getLineage** ( **_graph_** sourceVertices, **_int_** maxDepth, **_string_** direction )
  * Get the lineage from some source vertices.
  * _direction_ can be `'ancestor'`(or `'a'`) / `'descendant'` (or `'d'`) / `'both'` (or `'b'`).
  * E.g. `$2 = $base.getLineage($1, 3, 'b')`
* _graph_ **getPath** ( **_graph_** sourceVertices, **_graph_** destinationVertices, **_int_** maxDepth )
  * Get the path from some source vertices to some destination vertices.
  * E.g. `$3 = $base.getPath($1, $2, 5)`
* _graph_ **getShortestPath** ( **_graph_** sourceVertices, **_graph_** destinationVertices, **_int_** maxDepth )
  * Get the shortest path from some source vertices to some destination vertices.
  * _NOTE: This method would not find the real shortest path at this moment -- but just find "a short path"._
  * E.g. `$3 = $somePath.getShortestPath($1, $2, 5)`
* _graph_ **getSubgraph** ( **_graph_** skeletonGraph )
  * Get all the vertices and edges that are spanned by the skeleton graph.
  * E.g. `$2 = $base.getSubgraph($1)`
* _graph_ **limit** ( **_int_** limit)
  * Get the first (ordered by id) _limit_ vertices / edges.
  * E.g. `$2 = $1.limit(10)`
---
#### Functions
* _graph_ **vertices** ( **_string_** vertexHash, ... )
  * Get all vertices specified by the vertex hashes.
  * E.g. `$1 = vertices('815fd285f16cce9ab398b5b2ce5d2d03', '087b22992d4d871dc8c9ccd837132c6a')`
* _graph_ **edges** ( **_string_** edgeHash, ... )
  * Get all edges specified by the edge hashes.
  * E.g. `$1 = edges('b161b03a4365faf44d8cdd3713f811e9', 'b161b03a4365faf44d8cdd3713f811e0')`

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
