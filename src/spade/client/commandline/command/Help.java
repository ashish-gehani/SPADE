/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International
 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.
 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.client.commandline.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import spade.client.commandline.ExecutionContext;
import spade.client.commandline.command.exception.CommandExecutionNotComplete;
import spade.client.commandline.command.exception.IllegalCommand;
import spade.client.commandline.command.exception.IllegalCommandResult;


public class Help extends AbstractCommand {

    public static enum Type {
        ALL, CONTROL, CONSTRAINT, GRAPH, ENV, REMOTE
    }

    private final Type helpType;

    public Help(
        final Source source,
        final spade.client.commandline.command.Type type,
        final String raw,
        final Type helpType
    ) throws IllegalArgumentException {
        super(source, type, raw);
        this.helpType = helpType;
    }

    public final Type getHelpType() {
        return this.helpType;
    }

    public static Help create(final Source source, final String raw)
        throws IllegalArgumentException, IllegalCommand {
        if (raw == null) {
            throw new IllegalArgumentException("Raw query command cannot be null");
        }
        final Type helpType;
        final String expectedSyntax = "help [all | control | constraint | graph | env | remote]";
        final String [] toks = raw.split("\\s+", 2);
        if (toks.length == 1) {
            helpType = Type.ALL;
        } else if (toks.length == 2) {
            switch (toks[1]) {
                case "all":         helpType = Type.ALL; break;
                case "control":     helpType = Type.CONTROL; break;
                case "constraint":  helpType = Type.CONSTRAINT; break;
                case "graph":       helpType = Type.GRAPH; break;
                case "env":         helpType = Type.ENV; break;
                case "remote":      helpType = Type.REMOTE; break;
                default:
                    throw new IllegalCommand(
                        "Invalid 'help' syntax", expectedSyntax, toks[1]
                    );
            }
        } else {
            throw new IllegalCommand(
                "Invalid 'help' syntax", expectedSyntax, raw
            );
        }
        final Help instance = new Help(
            source,
            spade.client.commandline.command.Type.HELP,
            raw,
            helpType
        );
        return instance;
    }

    @Override
    protected synchronized Object executeInternal(final ExecutionContext ctx) throws IllegalArgumentException {
        if (ctx == null) {
            throw new IllegalArgumentException("Execution context cannot be null");
        }
        if (getHelpType() == null) {
            throw new IllegalArgumentException("Help type cannot be null");
        }
        return get(getHelpType());
    }

    @Override
    protected synchronized void writeExecutionResultInternal(
        final spade.client.commandline.output.User userOutput
    ) throws IllegalArgumentException, CommandExecutionNotComplete, IllegalCommandResult {
        if (userOutput == null) {
            throw new IllegalArgumentException("Null user output");
        }
        final Object resultObject = this.getExecutionResult();
        if (!(resultObject instanceof String)) {
            throw new IllegalCommandResult(
                "Unexpected command result. Expected: "
                + String.class.getName()
                + ". Actual: "
                + resultObject.getClass().getName()
            );
        }
        final String resultStr = (String)resultObject;
        userOutput.writeStringLn(resultStr);
    }

    private final String get(final Type type) throws IllegalArgumentException {
        if (type == null) {
            throw new IllegalArgumentException("NULL help type");
        }
        final String tab = "    ";
        final List<String> lines = new ArrayList<String>();
        lines.add("");
        switch (type) {
            case ALL: {
                lines.addAll(getControl(tab));
                lines.add("");
                lines.addAll(getEnvironmentVariables(tab));
                lines.add("");
                lines.addAll(getConstraint(tab));
                lines.add("");
                lines.addAll(getGraph(tab));
                lines.add("");
                lines.addAll(getRemoteVariables(tab));
            }
                break;
            case CONTROL: {
                lines.addAll(getControl(tab));
            }
                break;
            case CONSTRAINT: {
                lines.addAll(getConstraint(tab));
            }
                break;
            case GRAPH: {
                lines.addAll(getGraph(tab));
            }
                break;
            case ENV: {
                lines.addAll(getEnvironmentVariables(tab));
            }
                break;
            case REMOTE: {
                lines.addAll(getRemoteVariables(tab));
            }
                break;
            default:
                throw new IllegalArgumentException("Unknown type for 'help' command: " + type);
        }

        String linesAsString = "";
        for (String line : lines) {
            linesAsString += line + System.lineSeparator();
        }
        return linesAsString;
    }

    private final List<String> getEnvironmentVariables(final String tab) {
        return new ArrayList<String>(Arrays.asList(
            "Environment Variable(s) help:",
            tab + "env set <name> <number>",
            tab + "env unset <name>",
            tab + "env print <name>"
        ));
    }

    private final List<String> getRemoteVariables(final String tab) {
        return new ArrayList<String>(Arrays.asList(
            "Remote Variable(s) help:",
            tab + "remote create \"<remote host>\" <remote graph variable>",
            tab + "remote query \"<remote host>\" '<remote query string>'",
            tab + "remote link <local graph variable> \"<remote host>\" <remote graph variable>",
            tab + "remote unlink <local graph variable> \"<remote host>\" <remote graph variable>",
            tab + "remote clear <local graph variable>",
            tab + "remote copy <local src graph variable> <local dst graph variable>",
            tab + "remote list <local graph variable>",
            tab + "remote export <local graph variable>"
        ));
    }

    private final List<String> getControl(final String tab) {
        return new ArrayList<String>(Arrays.asList(
            "Control help:",
            tab + "set storage <Storage class name>",
            tab + "print storage",
            tab + "list [all | constraint | graph | env]",
            tab + "reset workspace",
            tab + "native '<Query to execute on the storage in single quotes>'",
            tab + "export > <Path of the file to write the output of next command to>",
            tab + "help [all | control | constraint | graph]",
            tab + "exit"
        ));
    }

    private final List<String> getConstraint(final String tab) {
        return new ArrayList<String>(Arrays.asList(
            "Constraint help:",
            tab + "Grammar:",
            tab + tab + "<Constraint Name> ::= %[a-zA-Z0-9_]+",
            tab + tab + "<Comparison Expression> ::= \".*\" < | <= | > | >= | == | != | like '.*'",
            tab + tab + "<Constraint Expression> ::= [ not ] <Comparison Expression> | [ not ] <Constraint Name>",
            tab + tab + "<Constraint> ::= <Constraint Expression> [ and | or <Constraint Expression> ]",
            tab + "Examples:",
            tab + tab + "%string_equal = \"annotation key\" == 'annotation value'",
            tab + tab + "%string_starts_with_fire = \"annotation key\" like 'fire%'",
            tab + tab + "%number_range_with_constraint_name = \"annotation key\" < '100' and %string_equal",
            tab + "Commands:",
            tab + tab + "list constraint",
            tab + tab + "dump %constraint_to_print",
            tab + tab + "erase %constraint_to_erase_1st ... %constraint_to_erase_nth"
        ));
    }

    private final List<String> getGraph(final String tab) {
        return new ArrayList<String>(Arrays.asList(
            "Graph help:",
            tab + "Methods:",
            tab + tab + "$vertices = $graph_to_get_vertices_from.getVertex(%optional_vertex_constraint_to_apply)",
            tab + tab + "$edges = $graph_to_get_edges_from.getEdge(%optional_edge_constraint_to_apply)",
            tab + tab
                    + "$collapsed_edges = $graph_to_collapse_edges_in.collapseEdge('1st edge annotation key', ... 'optional nth edge annotation key')",
            tab + tab + "$vertices = $graph_to_get_vertices_from.getEdgeEndpoints()",
            tab + tab + "$source_vertices = $graph_to_get_source_vertices_from.getEdgeSource()",
            tab + tab + "$destination_vertices = $graph_to_get_destination_vertices_from.getEdgeDestination()",
            tab + tab
                    + "$lineage = $graph_to_get_lineage_in.getLineage($source_vertices_graph [, max_depth_as_integer], 'a' | 'd' | 'b')",
            tab + tab
                    + "$neighbors = $graph_to_get_neighbors_in.getNeighbor($source_vertices_graph, 'a' | 'd' | 'b')",
            tab + tab
                    + "$paths = $graph_to_get_paths_in.getPath($source_vertices_graph, ($destination_vertices_graph [, max_depth_as_integer])+)",
            tab + tab
                    + "$shortest_paths = $graph_to_get_shortes_paths_in.getShortestPath($source_vertices_graph, $destination_vertices_graph [, max_depth_as_integer])",
            tab + tab
                    + "$vertices_in_skeketon_and_subject_graph_and_all_edges_between_them = $subject_graph.getSubgraph($skeleton_graph_to_get_vertices_from)",
            tab + tab + "$vertices_and_edges_limited_to_n = $subject_graph.limit(limit_as_integer)",
            tab + tab
                    + "$vertices_in_graphs_a_and_b_matching_on_annotation_values = $a.getMatch($b, '1st vertex annotation key', ... 'optional nth vertex annotation key')",
            tab + tab
                    + "$dependency_graph = $subject_graph.refineDependencies('path to dependency map file', 'edge annotation name'[, max_depth])",
            tab + "Functions:",
            tab + tab
                    + "$vertices = vertices('1st hex-encoded md5 hash of vertex', ... 'nth hex-encoded md5 hash of vertex')",
            tab + tab
                    + "$edges = edges('1st hex-encoded md5 hash of edge', ... 'nth hex-encoded md5 hash of edge')",
            tab + "Operations:",
            tab + tab + "$graph_1_and_2_union = $graph_1 + $graph_2",
            tab + tab + "$part_of_graph_2_not_in_graph_1 = $graph_2 - $graph_1",
            tab + tab + "$common_vertices_and_edges_in_graph_1_and_2 = $graph_1 & $graph_2",
            tab + "Commands:",
            tab + tab + "list graph",
            tab + tab + "stat $graph_to_print_vertex_count_and_edge_count_of",
            tab + tab + "dump [all] $graph_to_print_vertices_and_edges_of",
            tab + tab + "erase $graph_to_erase_1st ... $graph_to_erase_nth"
        ));
    }

}
