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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.SocketFactory;

import spade.core.SPADEQuery;

public final class RemoteSPADEQueryConnection implements Closeable{

	private static final Logger logger = Logger.getLogger(RemoteSPADEQueryConnection.class.getName());
	
	public final String localHostName;
	public final String serverAddress;
	public final int queryPort;
	
	private boolean connected = false;
	private String storageName = null;
	
	private Socket querySocket;
	private ObjectOutputStream queryWriter;
	private ObjectInputStream queryResponseReader;
	
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
	}
	
	@Override
	public synchronized void close() throws IOException{
		mustBeConnected();
		
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
	
	public synchronized SPADEQuery executeQuery(String queryString){
		return executeQuery(queryString, null);
	}
	
	public synchronized SPADEQuery executeQuery(String queryString, String nonce){
		return executeQuery(buildSPADEQueryObject(queryString, nonce));
	}
	
	public synchronized SPADEQuery executeQuery(SPADEQuery query){
		storageMustBeSet();
		return _executeQuery(query, false);
	}
	
	///////////////////////////
	
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
	private synchronized SPADEQuery _executeQuery(String query, boolean isExit){
		return _executeQuery(buildSPADEQueryObject(query, null), isExit);
	}
	
	private synchronized SPADEQuery _executeQuery(SPADEQuery query, boolean isExit){
		mustBeConnected();

		if(query == null){
			throw new RuntimeException("NULL query to execute!");
		}
		
		query.setQuerySentByClientAtMillis();
		
		try{
			queryWriter.writeObject(query);
			queryWriter.flush();
		}catch(Throwable t){
			throw new RuntimeException("Failed to send query to server", t);
		}
		
		if(isExit){
			// Don't read response
			query.setQueryReceivedBackByClientAtMillis();
			return query;
		}else{
			
			try{
				Object resultObject = queryResponseReader.readObject();
				query = (SPADEQuery)resultObject; // overwrite
			}catch(Throwable t){
				throw new RuntimeException("Failed to read query response from server", t);
			}
			
			query.setQueryReceivedBackByClientAtMillis();
			
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
	
	private synchronized SPADEQuery buildSPADEQueryObject(String query, String nonce){
		return new SPADEQuery(localHostName, serverAddress, query, nonce);
	}
}
