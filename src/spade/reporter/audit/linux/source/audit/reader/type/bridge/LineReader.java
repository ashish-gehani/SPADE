/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International

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
package spade.reporter.audit.linux.source.audit.reader.type.bridge;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.linux.source.audit.reader.Config;
import spade.reporter.audit.linux.source.audit.reader.Type;


public class LineReader extends spade.reporter.audit.linux.source.audit.reader.LineReader {

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private Process process;
	private BufferedReader reader;

	public LineReader (
		final Config config
	) throws Exception {
		super(Type.SPADE_AUDIT_BRIDGE);
		this.process = Helper.createProcess(config);
		this.process.start();
		this.reader = new BufferedReader(
			new InputStreamReader(process.getStdOutStream())
		);
	}

	@Override
	public String readLine() throws Exception{
		if (reader == null) {
			return null;
		}
		return reader.readLine();
	}

	@Override
	public void close(){
		if (reader != null) {
			try{
				reader.close();
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to close SPADE audit bridge reader", e);
			}
			reader = null;
		}

		if (process != null) {
			try{
				process.stop();
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to stop SPADE audit bridge process", e);
			}
			try{
				process.close();
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to close SPADE audit bridge process", e);
			}
			process = null;
		}
	}
}
