# Query

`Result` formats a `spade.core.Query` response (or its raw result/error object) into a
display string — a `Graph` result exported via `Graph.exportGraphToString`, a
`ResultTable` via its own `toString()`, anything else via `String.valueOf`, and a failed
query's error reduced to an `"Error: ..."` line. It also validates that a raw response
object actually is a `Query` before that formatting can run.

It exists because two independent callers need this exact mapping and previously
duplicated it: `spade.client.commandline.command.Server` (the interactive CLI's `server`
command, formatting a result as a string) and the MCP `query_object` connection type
(formatting a query response for a tool call result). Both take a `Query`, both branch on
success/failure and on the result's runtime type, and both only differed in which
`SaveGraph.Format` they exported a `Graph` result as — so that became a parameter instead
of a second copy of the logic. Both also received their `Query` as an untyped `Object`
(one from a command-execution result slot, one from an object-stream read) and needed the
same instanceof-check-and-cast before formatting could even start.
