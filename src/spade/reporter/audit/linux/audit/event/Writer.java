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
package spade.reporter.audit.linux.audit.event;

import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.core.event.MalformedEventException;
import spade.reporter.audit.linux.audit.event.record.Record;

/**
 * Writer implementation that writes Linux Audit Subsystem events to an OutputStream.
 *
 * Each record in the event is written as a single line.
 */
public class Writer extends spade.reporter.audit.core.event.Writer{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final PrintWriter printWriter;

	public Writer(final java.io.OutputStream outputStream){
		super();
		if(outputStream == null){
			throw new IllegalArgumentException("OutputStream cannot be NULL");
		}
		final boolean autoFlush = true;
		this.printWriter = new PrintWriter(outputStream, autoFlush);
	}

	/**
	 * Write an event to the output stream.
	 *
	 * Each record in the event is written as a line.
	 *
	 * @param event the audit event to write
	 * @throws Exception if writing fails
	 */
	@Override
	public void writeEvent(final spade.reporter.audit.core.event.Event event) throws Exception{
		if(event == null){
			return;
		}

		if(!(event instanceof Event)){
			throw new MalformedEventException(
				"Expected " + Event.class.getName() + " but got " + event.getClass().getName()
			);
		}

		final Event linuxEvent = (Event)event;
		for(final Record r : linuxEvent.getRecords()){
			if(r == null){
				throw new MalformedEventException("NULL record in event", linuxEvent.getId());
			}
			final String rStr = r.getRawRecord();
			if(rStr == null){
				throw new MalformedEventException("NULL raw record string in event", linuxEvent.getId());
			}
			printWriter.println(rStr);
		}
	}

	@Override
	public void close(){
		if(printWriter != null){
			try{
				printWriter.flush();
				printWriter.close();
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to close output stream writer", e);
			}
		}
	}
}
