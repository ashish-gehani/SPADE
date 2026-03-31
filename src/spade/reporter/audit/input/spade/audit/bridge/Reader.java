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
package spade.reporter.audit.input.spade.audit.bridge;

import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.las.event.Event;
import spade.reporter.audit.las.event.reader.InputStreamReader;

public class Reader extends spade.reporter.audit.input.Reader{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final Process process;
	private final InputStreamReader reader;

	public Reader (
		final Config config
	) throws Exception {
		super(config);
		this.process = Helper.createProcess(config);
		this.process.start();
		this.reader = new InputStreamReader(
			process.getStdOutStream(),
			config.getRecordFactory(),
			config.getEventFactory()
		);
	}

	@Override
	public Event readEvent() throws Exception{
		return reader.readEvent();
	}

	@Override
	public void close(){
		try{
			reader.close();
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to close SPADE audit bridge reader", e);
		}
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
	}
}
