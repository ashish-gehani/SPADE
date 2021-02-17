/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2017 SRI International
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
package spade.analyzer;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractAnalyzer;
import spade.core.AbstractStorage;
import spade.core.Kernel;
import spade.core.Query;
import spade.core.Settings;
import spade.query.quickgrail.QuickGrailExecutor;
import spade.utility.HelperFunctions;

/**
 * @author raza
 */
public class CommandLine extends AbstractAnalyzer{

	private static final Logger logger = Logger.getLogger(CommandLine.class.getName());
	private static final long millisWaitSocketClose = 100;

	// Current state of the CommandLine analyzer
	private volatile boolean shutdown = false;

	// Globals
	private ServerSocket queryServerListenerSocket = null;
	private final List<QueryConnection> queryClientConnections = new ArrayList<QueryConnection>();

	private void addQueryClientConnection(QueryConnection queryConnection){
		synchronized(queryClientConnections){
			if(queryConnection == null){
				return;
			}else{
				for(QueryConnection existingQueryConnection : queryClientConnections){
					if(existingQueryConnection == queryConnection){
						return;
					}
				}
				// Here only if it doesn't already exists
				queryClientConnections.add(queryConnection);
			}
		}
	}

	private void removeQueryClientConnection(QueryConnection queryConnection){
		synchronized(queryClientConnections){
			int index = -1;
			for(int a = 0; a < queryClientConnections.size(); a++){
				if(queryClientConnections.get(a) == queryConnection){
					index = a;
				}
			}
			if(index > -1){
				queryClientConnections.remove(index);
			}
		}
	}

	private void closeClientSocket(Socket socket){
		try{
			socket.close();
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to close query client socket", e);
		}
	}

	private void closeServerSocket(ServerSocket socket){
		try{
			socket.close();
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to close query server socket", e);
		}
	}

	@Override
	public final boolean initializeConcreteAnalyzer(String arguments){
		final int queryServerPort = Settings.getCommandLineQueryPort();
		try{
			this.queryServerListenerSocket = Kernel.createServerSocket(queryServerPort);
			try{
				Thread mainThread = new Thread(queryServerListenerRunnable,
						this.getClass().getSimpleName() + "AnalyzerServer-Thread");
				mainThread.start(); // Start
				logger.log(Level.INFO, "Query server listening on port: " + queryServerPort);
				return true;
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to start query server thread", e);
				try{
					this.queryServerListenerSocket.close();
				}catch(Exception e1){
					logger.log(Level.SEVERE, "Failed to close socket 'Query server socket'", e1);
				}
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to create query server socket", e);
		}
		return false;
	}

	private final Runnable queryServerListenerRunnable = new Runnable(){
		@Override
		public void run(){
			while(!shutdown){
				try{
					Socket queryClientSocket = queryServerListenerSocket.accept();
					try{
						QueryConnection thisConnection = new QueryConnection(queryClientSocket, Kernel.getDefaultQueryStorage());
						Thread connectionThread = new Thread(thisConnection);
						connectionThread.start(); // Start
						// Add to the list at the end
						addQueryClientConnection(thisConnection);
					}catch(Exception e){
						logger.log(Level.SEVERE, "Failed setup for accepted query client socket", e);
						closeClientSocket(queryClientSocket);
					}

				}catch(Exception e){
					if(shutdown){
						// here because the server socket was closed because of a shutdown
					}else{
						logger.log(Level.SEVERE, "Unexpected exception on query server socket", e);
						closeServerSocket(queryServerListenerSocket); // Close the server socket since we are stopping
					}
					break;
				}
			}

			shutdown();
		}
	};

	public synchronized final void shutdown(){
		if(!shutdown){
			shutdown = true;
			HelperFunctions.sleepSafe(millisWaitSocketClose);
			// Stop listening for any more client connections
			closeServerSocket(this.queryServerListenerSocket);
			HelperFunctions.sleepSafe(millisWaitSocketClose);
			// Close all the clients
			synchronized(queryClientConnections){
				for(QueryConnection queryConnection : new ArrayList<QueryConnection>(queryClientConnections)){
					queryConnection.shutdown();
				}
			}
		}
	}

	private class QueryConnection extends AbstractAnalyzer.QueryConnection{
		private final Socket clientSocket;
		private final ObjectOutputStream queryOutputWriter;
		private final ObjectInputStream queryInputReader;

		private volatile boolean queryClientShutdown = false;
		
		private QuickGrailExecutor quickGrailExecutor = null;

		private QueryConnection(Socket socket, AbstractStorage defaultStorageInKernel){
			if(socket == null){
				throw new IllegalArgumentException("NULL query client socket");
			}else{
				try{
					OutputStream outStream = socket.getOutputStream();
					InputStream inStream = socket.getInputStream();
					this.queryOutputWriter = new ObjectOutputStream(outStream);
					this.queryInputReader = new ObjectInputStream(inStream);
					this.clientSocket = socket;
				}catch(Exception e){
					throw new IllegalArgumentException("Failed to create query IO streams", e);
				}
			}

			if(defaultStorageInKernel != null){
				try{
					setCurrentStorage(defaultStorageInKernel);
					doQueryingSetupForCurrentStorage();
				}catch(Throwable t){
					logger.log(Level.SEVERE, "Failed to do storage query setup", t);
					try{
						doQueryingShutdownForCurrentStorage();
					}catch(Throwable t2){
						logger.log(Level.SEVERE, "Failed to do storage query shutdown", t);
					}
					setCurrentStorage(null);
				}
			}
		}

		@Override
		public Query readLineFromClient() throws Exception{
			return (Query)queryInputReader.readObject();
		}

		@Override
		public void writeToClient(Query query) throws Exception{
			queryOutputWriter.writeObject(query);
			queryOutputWriter.flush();
		}

		@Override
		public Query executeQuery(Query query) throws Exception{
			if(query != null){
				query = quickGrailExecutor.execute(query);
			}
			return query;
		}

		@Override
		public void doQueryingSetupForCurrentStorage() throws Exception{
			quickGrailExecutor = new QuickGrailExecutor(getCurrentStorage().getQueryInstructionExecutor());
		}

		@Override
		public void doQueryingShutdownForCurrentStorage() throws Exception{
			quickGrailExecutor = null;
		}
		
		@Override
		public String getQueryHelpTextAsString(final HelpType type) throws Exception{
			if(type == null){
				throw new RuntimeException("NULL help type");
			}
			final String tab = "    ";
			final List<String> lines = new ArrayList<String>();
			lines.add("");
			switch(type){
				case ALL:{
					lines.addAll(getControlHelp(tab));
					lines.add("");
					lines.addAll(getEnvironmentVariablesHelp(tab));
					lines.add("");
					lines.addAll(getConstraintHelp(tab));
					lines.add("");
					lines.addAll(getGraphHelp(tab));
				}
				break;
				case CONTROL:{
					lines.addAll(getControlHelp(tab));
				}
				break;
				case CONSTRAINT:{
					lines.addAll(getConstraintHelp(tab));
				}
				break;
				case GRAPH:{
					lines.addAll(getGraphHelp(tab));
				}
				break;
				case ENV:{
					lines.addAll(getEnvironmentVariablesHelp(tab));
				}
				break;
				default: throw new RuntimeException("Unknown type for help command: " + type);
			}
			
			String linesAsString = "";
			for(String line : lines){
				linesAsString += line + System.lineSeparator();
			}
			return linesAsString;
		}
		
		private final List<String> getEnvironmentVariablesHelp(final String tab){
			return new ArrayList<String>(Arrays.asList(
					"Environment Variable(s) help:",
					tab + "env set maxDepth|limit <number>",
					tab + "env unset maxDepth|limit",
					tab + "env print maxDepth|limit"
					));
		}
		
		private final List<String> getControlHelp(final String tab){
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
		
		private final List<String> getConstraintHelp(final String tab){
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
		
		private final List<String> getGraphHelp(final String tab){
			return new ArrayList<String>(Arrays.asList(
					"Graph help:",
					tab + "Methods:",
					tab + tab + "$vertices = $graph_to_get_vertices_from.getVertex(%optional_vertex_constraint_to_apply)",
					tab + tab + "$edges = $graph_to_get_edges_from.getEdge(%optional_edge_constraint_to_apply)",
					tab + tab + "$collapsed_edges = $graph_to_collapse_edges_in.collapseEdge('1st edge annotation key', ... 'optional nth edge annotation key')",
					tab + tab + "$vertices = $graph_to_get_vertices_from.getEdgeEndpoints()",
					tab + tab + "$source_vertices = $graph_to_get_source_vertices_from.getEdgeSource()",
					tab + tab + "$destination_vertices = $graph_to_get_destination_vertices_from.getEdgeDestination()",
					tab + tab + "$lineage = $graph_to_get_lineage_in.getLineage($source_vertices_graph [, max_depth_as_integer], 'a' | 'd' | 'b')",
					tab + tab + "$neighbors = $graph_to_get_neighbors_in.getNeighbor($source_vertices_graph, 'a' | 'd' | 'b')",
					tab + tab + "$paths = $graph_to_get_paths_in.getPath($source_vertices_graph, ($destination_vertices_graph [, max_depth_as_integer])+)",
					tab + tab + "$shortest_paths = $graph_to_get_shortes_paths_in.getShortestPath($source_vertices_graph, $destination_vertices_graph [, max_depth_as_integer])",
					tab + tab + "$vertices_in_skeketon_and_subject_graph_and_all_edges_between_them = $subject_graph.getSubgraph($skeleton_graph_to_get_vertices_from)",
					tab + tab + "$vertices_and_edges_limited_to_n = $subject_graph.limit(limit_as_integer)",
					tab + tab + "$vertices_in_graphs_a_and_b_matching_on_annotation_values = $a.getMatch($b, '1st vertex annotation key', ... 'optional nth vertex annotation key')",
					tab + "Functions:",
					tab + tab + "$vertices = vertices('1st hex-encoded md5 hash of vertex', ... 'nth hex-encoded md5 hash of vertex')",
					tab + tab + "$edges = edges('1st hex-encoded md5 hash of edge', ... 'nth hex-encoded md5 hash of edge')",
					tab + "Operations:",
					tab + tab + "$graph_1_and_2_union = $graph_1 + $graph_2",
					tab + tab + "$part_of_graph_2_not_in_graph_1 = $graph_2 - $graph_1",
					tab + tab + "$common_vertices_and_edges_in_graph_1_and_2 = $graph_1 & $graph_2",
					tab + "Commands:",
					tab + tab + "list graph",
					tab + tab + "stat $graph_to_print_vertex_count_and_edge_count_of",
					tab + tab + "dump [force] $graph_to_print_vertices_and_edges_of",
					tab + tab + "visualize [force] $graph_to_print_vertices_and_edges_of_in_dot_format",
					tab + tab + "erase $graph_to_erase_1st ... $graph_to_erase_nth"
					));
		}

		@Override
		public synchronized void shutdown(){
			if(!queryClientShutdown){
				queryClientShutdown = true;
				try{
					queryOutputWriter.close();
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to close query output stream", e);
				}
				try{
					queryInputReader.close();
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to close query input stream", e);
				}
				closeClientSocket(this.clientSocket);
				removeQueryClientConnection(this);
			}
		}

		@Override
		public boolean isShutdown(){
			return queryClientShutdown;
		}
	}
}

/*
 * How to use discrepancy-dev branch:
 * 
 * 1) 2 machines. file send between the two machines
 * 2) copy cfg/keys/public/self.*.public to cfg/keys/public/<hostname>.*.public to the each other host
 * 2) same network artifacts in both graphs on the machines (complete one)
 * 4) get lineage that would go to the remote host
 * 5) remote resolution would be set automatically
 * 6) true should be in find_inconsistency.txt file
 * 7) to introduce discrepancy -> 
 * 	a) get lineage q1 with real data
 *  b) get lineage q2 with the deletion of an edge or a vertex from the result of q1 previously gotten
 */
                 
