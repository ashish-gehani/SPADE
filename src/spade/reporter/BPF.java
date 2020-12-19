/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

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
package spade.reporter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
import spade.core.Settings;
import spade.edge.opm.Used;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.utility.HelperFunctions;
import spade.utility.Result;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

public class BPF extends AbstractReporter{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final String keyArgumentPort = "port";
	private final String keyType = "type", keyTypeEnter = "enter", keyTypeExit = "exit", keyNanos = "nanos",
			keyPid = "pid", keySrc = "src", keyFunction = "function", keyArguments = "arguments", keyName = "name",
			keyValue = "value", keyReturn = "return", keyIndex = "index";

	private volatile boolean shutdown;

	private ServerSocket server;

	private final Map<String, Stack<Process>> functionStackMap = Collections
			.synchronizedMap(new HashMap<String, Stack<Process>>());

	public static final int THREAD_SLEEP_DELAY = 400;
	public final ArrayList<BufferedReader> threadBufferReaders = new ArrayList<BufferedReader>();

	@Override
	public boolean launch(final String arguments){
		final String configFilePath = Settings.getDefaultConfigFilePath(this.getClass());
		final Map<String, String> map;
		try{
			map = HelperFunctions.parseKeyValuePairsFrom(arguments, configFilePath, null);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to parse arguments or read config file", e);
			return false;
		}

		final Result<Long> portResult = HelperFunctions.parseLong(map.get(keyArgumentPort), 10, 0, 65536);
		if(portResult.error){
			logger.log(Level.SEVERE, "Invalid value for key '" + keyArgumentPort + "': " + portResult.toErrorString());
			return false;
		}
		final int port = portResult.result.intValue();

		try{
			server = new ServerSocket(port);
			server.setReuseAddress(true);
			shutdown = false;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to create server connection on given port", e);
			return false;
		}

		try{
			// Create connectionThread to listen on the socket and create EventHandlers
			final Runnable connectionThread = new Runnable(){
				public void run(){
					while(!shutdown){
						try{
							final Socket socket = server.accept();
							final EventHandler eventHandler = new EventHandler(socket);
							new Thread(eventHandler).start();
							Thread.sleep(THREAD_SLEEP_DELAY);
						}catch(Exception e){
							logger.log(Level.SEVERE, "Failed to handle a client connection accept", e);
						}
					}
				}
			};
			new Thread(connectionThread).start();
			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to start client connection listener thread", e);
			return false;
		}

	}

	@Override
	public boolean shutdown(){
		shutdown = true;
		try{
			if(server != null){
				server.close();
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to close server connection gracefully", e);
		}
		return true;
	}

	private class EventHandler implements Runnable{

		private final Socket threadSocket;
		private BufferedReader inFromClient;

		private EventHandler(Socket socket){
			threadSocket = socket;
		}

		@Override
		public void run(){
			try{
				inFromClient = new BufferedReader(new InputStreamReader(threadSocket.getInputStream()));
				threadBufferReaders.add(inFromClient);
				int adaptitive_pause_step = 10;
				int adaptitive_pause = 10;
				while(!shutdown || inFromClient.ready()){
					String line = inFromClient.readLine();
					if(line != null){
						parseEvent(line);
						adaptitive_pause = 0;
					}else{
						Thread.sleep(adaptitive_pause);
						if(adaptitive_pause < THREAD_SLEEP_DELAY){
							adaptitive_pause += adaptitive_pause_step;
						}
					}
				}
				threadBufferReaders.add(inFromClient);
				inFromClient.close();
				threadSocket.close();
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to read from client", e);
			}
		}

		private void parseEvent(String line) throws Exception{
			final Object object;
			try{
				final JSONTokener jsonTokener = new JSONTokener(line);
				object = jsonTokener.nextValue();
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to read JSON object", e);
				return;
			}
			if(object == null){
				return;
			}
			if(!object.getClass().equals(JSONObject.class)){
				logger.log(Level.SEVERE, "Unexpected JSON element '" + object + "'. Expected JSON object");
				return;
			}
			final JSONObject jsonObject = (JSONObject)object;

			final String eventType = jsonObject.getString(keyType);
			final String pid = jsonObject.getString(keyPid);
			final String nanos = jsonObject.getString(keyNanos);

			if(!functionStackMap.containsKey(pid)){
				functionStackMap.put(pid, new Stack<Process>());
			}

			if(keyTypeEnter.equals(eventType)){
				final String srcFile = jsonObject.getString(keySrc);
				final String functionName = jsonObject.getString(keyFunction);
				final String functionId = srcFile + "." + functionName + "." + pid;

				final Process function = new Process();
				function.addAnnotation("FunctionID", functionId);
				function.addAnnotation("FunctionName", functionName);
				function.addAnnotation("ThreadID", pid);
				putVertex(function);

				final JSONArray argsJsonArray = jsonObject.getJSONArray(keyArguments);
				for(int i = 0; i < argsJsonArray.length(); i++){
					final JSONObject argJsonObject = argsJsonArray.getJSONObject(i);
					final String argName = argJsonObject.getString(keyName);
					final String argValue = argJsonObject.getString(keyValue);
					final String argIndex = argJsonObject.getString(keyIndex);
					final String argType = argJsonObject.getString(keyType);

					final String argId = functionId + "." + argIndex;

					final Artifact argument = new Artifact();
					argument.addAnnotation("ID", argId);
					argument.addAnnotation("ArgType", argType);
					argument.addAnnotation("ArgName", argName);
					argument.addAnnotation("ArgVal", argValue);
					putVertex(argument);

					if(!functionStackMap.get(pid).isEmpty()){
						final AbstractEdge wgbEdge = new WasGeneratedBy(argument, functionStackMap.get(pid).peek());
						wgbEdge.addAnnotation("Nanos", nanos);
						putEdge(wgbEdge);
					}
					final AbstractEdge usedEdge = new Used(function, argument);
					usedEdge.addAnnotation("Nanos", nanos);
					putEdge(usedEdge);
				}

				if(!functionStackMap.get(pid).empty()){
					final WasTriggeredBy wtbEdge = new WasTriggeredBy(function, functionStackMap.get(pid).peek());
					wtbEdge.addAnnotation("Nanos", nanos);
					putEdge(wtbEdge);
				}
				functionStackMap.get(pid).push(function);
			}else if(keyTypeExit.equals(eventType)){
				final JSONObject returnJSONObject = jsonObject.getJSONObject(keyReturn);
				if(!returnJSONObject.isNull(keyValue)){
					final Artifact argument = new Artifact();
					argument.addAnnotation("ReturnType", returnJSONObject.getString(keyType));
					argument.addAnnotation("ReturnVal", returnJSONObject.getString(keyValue));
					putVertex(argument);
					final WasGeneratedBy wgbEdge = new WasGeneratedBy(argument, functionStackMap.get(pid).peek());
					wgbEdge.addAnnotation("Nanos", nanos);
					putEdge(wgbEdge);
				}
				functionStackMap.get(pid).pop();
			}
		}
	}
}
