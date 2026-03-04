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

import java.net.ServerSocket;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.analyzer.commandline.server.Connection;
import spade.analyzer.commandline.server.State;
import spade.core.AbstractAnalyzer;
import spade.core.Kernel;
import spade.core.Settings;
import spade.core.analyzer.RequiredConfig;

/**
 * @author raza
 */
public class CommandLine extends AbstractAnalyzer{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private Connection serverConn = null;

	@Override
	public final boolean initialize(final String arguments) {
		int queryServerPort;
		RequiredConfig requiredConfig = null;
		ServerSocket serverSocket = null;

		try {
			requiredConfig = loadRequiredConfig(arguments);
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to load required config", e);
			return false;
		}

		try {
			queryServerPort = Settings.getCommandLineQueryPort();
			serverSocket = Kernel.createServerSocket(queryServerPort);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to create query server socket", e);
			return false;
		}

		try {
			this.serverConn = new Connection(
				serverSocket, new State(), requiredConfig
			);
			final Thread serverThread = new Thread(
				this.serverConn,
				this.getClass().getSimpleName() + "AnalyzerServer-Thread"
			);
			serverThread.start();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to start server connection thread", e);
			if (serverSocket != null) {
				try {
					serverSocket.close();
				} catch (Exception eInner) {

				}
			}
			return false;
		}

		logger.log(Level.INFO, "Query server listening on port: " + queryServerPort);
		return true;
	}

	@Override
	public void shutdown() {
		if (this.serverConn != null) {
			serverConn.stop();
		}
	}

	@Override
	public boolean isShutdown() {
		if (this.serverConn != null) {
			return !serverConn.isRunning();
		}
		return true;
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
                 
