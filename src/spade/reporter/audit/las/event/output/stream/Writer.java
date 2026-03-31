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
package spade.reporter.audit.las.event.output.stream;

import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.las.event.Event;
import spade.reporter.audit.las.event.MalformedEventException;
import spade.reporter.audit.las.event.record.Record;

/**
 * Writer implementation that writes audit record lines to an OutputStream.
 *
 * Each record in the event is written as a single line.
 */
public class Writer extends spade.reporter.audit.las.event.output.Writer{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final PrintWriter printWriter;

	public Writer(final java.io.OutputStream outputStream){
		super(outputStream);
		final boolean autoFlush = true;
		this.printWriter = new PrintWriter(outputStream, autoFlush);
	}

	/**
	 * Write an event to the output stream.
	 *
	 * Each record in the event is written as a line.
	 *
	 * @param event the audit event to write
	 * @return number of bytes written
	 * @throws Exception if writing fails
	 */
	@Override
	public long writeEvent(final Event event) throws Exception{
		if(event == null){
			return 0;
		}

		long bytesWritten = 0;
		for(final Record r : event.getRecords()){
			if(r == null){
				throw new MalformedEventException("NULL record in event");
			}
			final String rStr = r.getRawRecord();
			if(rStr == null){
				throw new MalformedEventException("NULL raw record string in event");
			}
			printWriter.println(rStr);
			bytesWritten += rStr.length() + 1;
		}
		return bytesWritten;
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
