# QuickGrail Reference

## Basic Examples
```
# Find the nodes that we are interested in.
$start_ip = $base.getVertex(* like '%128.55.12.104%');
$elevate_me = $base.getVertex(* like '%elevateme%');

# Find the paths from $elevate_me to $start_ip.
$si2em = $base.getPath($elevate_me, $start_ip, 4);

# Output the subgraph.
dump $si2em;
```

## Lexical Structure
* Integer Literals
  * E.g. `1234`
* String Literals
  * E.g. `'abc'`, `'%127.0.0.1%'`
* Name
  * E.g. `dump`, `type`, `*` (_yes "star" is a name_..), `"a name with space and double quotes"`
* Operators
  * E.g. `=`, `+`, `+=`, `-`, `&`, `==`
* Graph Variables
  * E.g. `$ip`, `$1`
    * `$base` is a special immutable variable that represents the overall graph.
* Graph Meta-data Variables
  * E.g. `@ip`, `@1`

## Expressions
* Graph Expressions
  * _graph_ ::= _graph-variable_
  * _graph_ ::= _graph_ `.` _graph-method_ `(` _argument-list_ `)`
  * _graph_ ::= _graph_ ( `+` | `-` | `&` ) _graph_
* Graph Predicates
  * _graph-predicate_ ::= _name_ ( `=` / `<>` / `>` / `<` / `>=` / `<=` | `like` | `regexp` ) _string-literal_
  * _graph-predicate_ ::= _graph-predicate_ ( `and` | `or` ) _graph-predicate_
  * _graph-predicate_ ::= `not` _graph-predicate_
  * _graph-predicate_ ::= `(` _graph-predicate_ `)`

## Commands
* List all existing graphs.
  * `list`
* Output graph statistics.
  * `stat` _graph_
* Output graph as a SPADE `Graph`.
  * `dump` _graph_
* Output graph in DOT format.
  * `visualize` _graph_
* Remove a list of variables and the corresponding tables.
  * `erase` (_graph_)+
* Remove all variables and the corresponding tables.
  * `reset workspace`

## Methods
#### Method Declaration Notation

_return-type_ **method-name** ( **_argument-type_** formal-argument, ... )

---

#### Graph Methods
* _graph_ **getVertex** ( )
  * Get all the vertices.
  * E.g. `$2 = $1.getVertex()`
* _graph_ **getVertex** ( **_graph-predicate_** predicate )
  * Get all the vertices that match a predicate.
  * E.g. `$2 = $1.getVertex(* LIKE '%firefox%')`
* _graph_ **getEdge** ( )
  * Get all the edges.
  * E.g. `$2 = $1.getEdge()`
* _graph_ **getEdge** ( **_graph-predicate_** predicate )
  * Get all the edges that match a predicate.
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
* _graph_ **span** ( **_graph_** sourceGraph )
  * Get all the vertices and edges from _sourceGraph_ that are spanned by _this_ graph.
  * This method is symmetric to getSubgraph, i.e. `$b.span($a)` is equivalent to `$a.getSubgraph($b)`.
  * E.g. `$2 = $1.span($base)`
* _graph_ **limit** ( **_int_** limit)
  * Get the first (ordered by id) _limit_ vertices / edges.
  * E.g. `$2 = $1.limit(10)`
---
#### Functions
* _graph_ **vertices** ( **_int_** vertexId, ... )
  * Get all vertices specified by the vertex ids.
  * E.g. `$1 = vertices(31739934, 31740737, 31740738).span($base)`
* _graph_ **edges** ( **_int_** edgeId, ... )
  * Get all edges specified by the edge ids.
  * E.g. `$1 = edges(121997607, 121997288).span($base)`
* _graph_ **asVertex** ( **_string_** sqlQuery )
  * Evaluate _sqlQuery_ and use the result table as vertex ids for the result graph.
  * E.g. _Get the top 10 vertices which have the largest indegree:_
    ```
    $top10_largest_indegree =
        asVertex('SELECT dst FROM edge GROUP by dst ORDER BY COUNT(src) DESC LIMIT 10;')
    ```
  * Can refer to _graph-variable_:
    ```
    $1 = $base.getLineage($base.getVertex(* LIKE '%elevateme%'), 1, 'b');
    $2 = asVertex('SELECT id FROM $1.vertex;');
    $3 = asVertex('SELECT src FROM edge WHERE id IN (SELECT id FROM $1.edge);') +
         asVertex('SELECT dst FROM edge WHERE id IN (SELECT id FROM $1.edge);');
    ```
* _graph_ **asEdge** ( **_string_** sqlQuery )
  * Evaluate _sqlQuery_ and use the result table as edge ids for the result graph.
  * Similar to _asVertex_.

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
