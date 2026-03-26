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
package spade.reporter.audit.las.event.writer;

import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.las.event.Event;
import spade.reporter.audit.las.event.MalformedEventException;
import spade.reporter.audit.las.event.record.Record;

/**
 * Writer implementation that writes audit record lines to a file.
 *
 * Supports log rotation based on estimated file size (cumulative bytes written).
 * File naming: basePath, basePath.1, basePath.2, ...
 */
public class File extends Writer{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final FileConfig config;

	private PrintWriter currentWriter;
	private int currentFileIndex;
	private long estimatedBytesWritten;

	public File(final FileConfig config) throws Exception{
		super(config.isVerbose());
		this.config = config;
		this.currentFileIndex = 0;
		this.estimatedBytesWritten = 0;
		this.currentWriter = new PrintWriter(config.getFilePath());
	}

	/**
	 * Write an event to the output file.
	 *
	 * After writing, checks if rotation threshold is exceeded and rotates if needed.
	 * Byte estimation: line.length() + 1 (for newline), assuming ASCII/UTF-8 audit data.
	 */
	@Override
	public void writeEvent(final Event event) throws Exception{
		if (currentWriter == null) {
			return;
		}
		if (event == null) {
			return;
		}

		internalWriteEvent(event);

		if(
			config.isRotationEnabled()
			&& estimatedBytesWritten >= config.getRotateAfterEstimatedBytes()
		){
			if(isVerbose()){
				logger.log(Level.FINE, "Rotating output file. Estimated bytes: {0}, threshold: {1}",
						new Object[]{estimatedBytesWritten, config.getRotateAfterEstimatedBytes()});
			}
			currentWriter.flush();
			currentWriter.close();
			currentFileIndex++;
			estimatedBytesWritten = 0;
			currentWriter = new PrintWriter(
				config.getFilePath() + "." + currentFileIndex
			);
		}
	}

	/**
	 * Write an event to the output file.
	 *
	 * Returns the number of bytes written.
	 */
	private void internalWriteEvent(final Event event) throws Exception {
		for (final Record r : event.getRecords()) {
			if (r == null) {
				throw new MalformedEventException("NULL record in event");
			}
			final String rStr = r.getRawRecord();
			if (rStr == null) {
				throw new MalformedEventException("NULL raw record string in event");
			}
			currentWriter.println(rStr);
			estimatedBytesWritten += rStr.length() + 1;
		}
	}

	@Override
	public void close(){
		if (currentWriter == null) {
			return;
		}
		try{
			currentWriter.flush();
			currentWriter.close();
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to close output file writer", e);
		}
	}
}
