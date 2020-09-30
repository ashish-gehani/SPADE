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

package spade.utility;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.SocketFactory;

import spade.core.AbstractStorage;
import spade.core.Query;
import spade.query.quickgrail.core.GraphStats;
import spade.query.quickgrail.instruction.GetLineage;

public final class RemoteSPADEQueryConnection implements Closeable{

	private static final Logger logger = Logger.getLogger(RemoteSPADEQueryConnection.class.getName());

	private static final String baseSymbol = "$base";
	
	public final String localHostName;
	public final String serverAddress;
	public final int queryPort;
	
	private boolean connected = false;
	private String storageName = null;
	
	private Socket querySocket;
	private ObjectOutputStream queryWriter;
	private ObjectInputStream queryResponseReader;
	
	private final int symbolId;
	private final Set<String> generatedSymbols = new HashSet<String>();
	
	public RemoteSPADEQueryConnection(String localHostName, String serverAddress, int queryPort) throws Exception{
		this.localHostName = localHostName; // can be null
		this.serverAddress = serverAddress;
		this.queryPort = queryPort;
		
		if(HelperFunctions.isNullOrEmpty(serverAddress)){
			throw new RuntimeException("NULL/Empty server address: '"+serverAddress+"'");
		}
		
		if(queryPort < 0){
			throw new RuntimeException("Invalid query port: '" + queryPort + "'");
		}
		
		symbolId = new Random(System.nanoTime()).nextInt(999);
	}
	
	private synchronized final String generateSymbol(){
		String symbol = "$gen_"+symbolId+"_"+(new Random(System.nanoTime()).nextInt(999));
		generatedSymbols.add(symbol);
		return symbol;
	}
	
	private synchronized final void removeSymbol(String symbol){
		generatedSymbols.remove(symbol);
	}
	
	public synchronized void connect(final SocketFactory socketFactory, final int timeoutInMillis) throws Exception{
		mustNotBeConnected();
		
		if(socketFactory == null){
			throw new RuntimeException("NULL socket factory");
		}
		
		Socket socket = null;
		
		try{
			socket = socketFactory.createSocket();
			_connect(socket, timeoutInMillis);
		}catch(Exception e){
			if(socket != null){
				try{ if(!socket.isClosed()){ socket.close(); } }catch(Exception e0){}
			}
			throw e;
		}
	}
	
	private synchronized void _connect(final Socket socket, final int timeoutInMillis) throws Exception{
		mustNotBeConnected();
		
		if(socket == null){
			throw new RuntimeException("NULL query socket");
		}
		
		try{
			this.querySocket = socket;
			this.querySocket.connect(new InetSocketAddress(serverAddress, queryPort), timeoutInMillis);
			this.queryWriter = new ObjectOutputStream(querySocket.getOutputStream());
			this.queryResponseReader = new ObjectInputStream(querySocket.getInputStream());
		}catch(Exception e){
			try{ if(queryResponseReader != null){ queryResponseReader.close(); } }catch(Exception e0){}
			try{ if(queryWriter != null){ queryWriter.close(); } }catch(Exception e0){}
			try{ if(querySocket != null){ querySocket.close(); } }catch(Exception e0){}
			this.querySocket = null;
			this.queryWriter = null;
			this.queryResponseReader = null;
			
			throw e;
		}
		
		connected = true;
		
		try{
			Query result = _executeQuery("print storage", false);
			if(result.wasQuerySuccessful()){
				try{
					@SuppressWarnings("unchecked")
					Class<? extends AbstractStorage> storageClass = 
							(Class<? extends AbstractStorage>)Class.forName("spade.storage."+String.valueOf(result.getResult()));
					this.storageName = storageClass.getSimpleName();
				}catch(Throwable t){
					// ignore. user must set it
				}
			}
		}catch(Throwable t){
			// ignore. user must set it
		}
	}
	
	@Override
	public synchronized void close() throws IOException{
		mustBeConnected();
		
		if(!generatedSymbols.isEmpty()){
			String str = "";
			for(String symbol : generatedSymbols){
				str += " " + symbol;
			}
			try{
				_executeQuery("erase " + str, false);
			}catch(Throwable t){
				logger.log(Level.WARNING, "Failed to execute 'erase' query", t);
			}
			generatedSymbols.clear();
		}
		
		try{
			_executeQuery("exit", true);
		}catch(Throwable t){
			logger.log(Level.WARNING, "Failed to execute 'exit' query", t);
		}
		
		connected = false;
		storageName = null;
		
		try{
			queryWriter.close();
			queryWriter = null;
		}catch(Throwable t){
			logger.log(Level.WARNING, "Failed to close connection writer", t);
		}
		try{
			queryResponseReader.close();
			queryResponseReader = null;
		}catch(Throwable t){
			logger.log(Level.WARNING, "Failed to close connection reader", t);
		}
		try{
			querySocket.close();
			querySocket = null;
		}catch(Throwable t){
			logger.log(Level.WARNING, "Failed to close connection", t);
		}
	}
	
	//////////////////////////////
	
	public synchronized boolean isStorageSet(){
		return storageName != null;
	}
	
	public synchronized String getStorageName(){
		return storageName;
	}
	
	public synchronized GraphStats statGraph(String symbol){
		Query response = executeQuery("stat " + symbol);
		return (GraphStats)response.getResult();
	}
	
	public synchronized String getBaseVertices(String predicate){
		return getVertices(baseSymbol, predicate);
	}
	
	public synchronized String getVertices(String subgraphSymbol, String predicate){
		return _getVertices(subgraphSymbol, predicate);
	}
	
	public synchronized String getBaseLineage(String startSymbol, int depth, GetLineage.Direction direction){
		return getLineage(baseSymbol, startSymbol, depth, direction);
	}
	
	public synchronized String getLineage(String subgraphSymbol, String startSymbol, int depth, GetLineage.Direction direction){
		return _getLineage(subgraphSymbol, startSymbol, depth, direction);
	}
	
	public synchronized spade.core.Graph exportGraph(final String symbol){
		return exportGraph(symbol, true);
	}
	
	public synchronized spade.core.Graph exportGraph(final String symbol, final boolean verify){
		final String nonce = String.valueOf(System.nanoTime());
		Query response = executeQuery("dump force " + symbol, nonce);
		spade.core.Graph graph = (spade.core.Graph)response.getResult();
		if(verify){
			if(!graph.verifySignature(nonce)){
				throw new RuntimeException("Failed to verify signature. Response graph discarded");
			}
		}
		return graph;
	}
	
	public synchronized Query executeQuery(String queryString){
		return executeQuery(queryString, null);
	}
	
	public synchronized Query executeQuery(String queryString, String nonce){
		return executeQuery(buildSPADEQueryObject(queryString, nonce));
	}
	
	public synchronized Query executeQuery(Query query){
		storageMustBeSet();
		return _executeQuery(query, false);
	}
	
	///////////////////////////
	
	private synchronized String _getVertices(String subgraphSymbol, String predicate){
		return _executeAssignment(subgraphSymbol + ".getVertex(" + predicate + ")");
	}
	
	private synchronized String _getLineage(String subgraphSymbol, String startSymbol, int depth, GetLineage.Direction direction){
		return _executeAssignment(subgraphSymbol 
				+ ".getLineage("+startSymbol+", "+depth+", '"+direction.toString().toLowerCase().charAt(1)+"')");
	}
	
	private synchronized String _executeAssignment(String rhs){
		String newSymbol = generateSymbol();
		try{
			String query = newSymbol + " = " + rhs + ";";
			executeQuery(query);
			return newSymbol;
		}catch(Throwable t){
			removeSymbol(newSymbol);
			throw t;
		}
	}
	
	private synchronized void mustBeConnected(){
		if(!connected){
			throw new RuntimeException("Remote query connection not established!");
		}
	}
	
	private synchronized void mustNotBeConnected(){
		if(connected){
			throw new RuntimeException("Connection already established!");
		}
	}
	
	private synchronized void storageMustBeSet(){
		if(!isStorageSet()){
			throw new RuntimeException("No storage set!");
		}
	}
	
	public synchronized void setStorage(String storageName){
		if(HelperFunctions.isNullOrEmpty(storageName)){
			throw new RuntimeException("NULL/Empty storage name: '"+storageName+"'");
		}
		_executeQuery("set storage " + storageName, false);
		this.storageName = storageName;
	}
	
	// isExit tells it to not read response
	private synchronized Query _executeQuery(String query, boolean isExit){
		return _executeQuery(buildSPADEQueryObject(query, null), isExit);
	}
	
	private synchronized Query _executeQuery(Query query, boolean isExit){
		mustBeConnected();

		if(query == null){
			throw new RuntimeException("NULL query to execute!");
		}

		try{
			queryWriter.writeObject(query);
			queryWriter.flush();
		}catch(Throwable t){
			throw new RuntimeException("Failed to send query to server", t);
		}
		
		if(isExit){
			// Don't read response
			return query;
		}else{
			
			try{
				Object resultObject = queryResponseReader.readObject();
				query = (Query)resultObject; // overwrite
			}catch(Throwable t){
				throw new RuntimeException("Failed to read query response from server", t);
			}
			
			if(!query.wasQuerySuccessful()){
				Object error = query.getError();
				if(error == null){
					throw new RuntimeException("Query failed without any error!");
				}else{
					if(error instanceof Throwable){
						throw new RuntimeException("Query failed!", (Throwable)error);
					}else{
						throw new RuntimeException("Query failed! Error: " + String.valueOf(error));
					}
				}
			}else{
				return query;
			}
			
		}
	}
	
	private synchronized Query buildSPADEQueryObject(String query, String nonce){
		return new Query(localHostName, serverAddress, query, nonce);
	}
}
