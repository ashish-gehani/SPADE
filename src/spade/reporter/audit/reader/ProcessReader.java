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
package spade.reporter.audit.reader;

import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.las.event.Event;
import spade.reporter.audit.las.event.reader.InputStreamReader;

/**
 * Reads audit events from the stdout of a process using InputStreamReader.
 */
public class ProcessReader extends Reader{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final InputStreamReader reader;

	public ProcessReader(
		final Process process,
		final spade.reporter.audit.las.event.record.Factory recordFactory,
		final spade.reporter.audit.las.event.Factory eventFactory
	) throws Exception{
		if(process == null){
			throw new IllegalArgumentException("Process cannot be NULL");
		}
		if(recordFactory == null){
			throw new IllegalArgumentException("Record factory cannot be NULL");
		}
		if(eventFactory == null){
			throw new IllegalArgumentException("Event factory cannot be NULL");
		}
		this.reader = new InputStreamReader(
			process.getInputStream(),
			recordFactory,
			eventFactory
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
			logger.log(Level.SEVERE, "Failed to close process reader", e);
		}
	}
}
