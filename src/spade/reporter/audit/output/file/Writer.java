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
package spade.reporter.audit.output.file;

import java.io.FileOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.las.event.Event;

/**
 * Writes audit events to a file using OutputStreamWriter.
 */
public class Writer extends spade.reporter.audit.output.Writer{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final spade.reporter.audit.las.event.output.stream.Writer writer;

	public Writer(final Config config) throws Exception{
		super(config);
		this.writer = new spade.reporter.audit.las.event.output.stream.Writer(
			new FileOutputStream(config.getFilePath())
		);
	}

	@Override
	public long writeEvent(final Event event) throws Exception{
		return writer.writeEvent(event);
	}

	@Override
	public void close(){
		try{
			writer.close();
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to close file writer", e);
		}
	}
}
