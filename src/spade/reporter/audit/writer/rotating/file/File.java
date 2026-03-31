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
package spade.reporter.audit.writer.rotating.file;

import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.las.event.Event;
import spade.reporter.audit.writer.Writer;

/**
 * Writes audit events to a file with rotation based on bytes written.
 *
 * File naming: basePath, basePath.1, basePath.2, ...
 */
public class File extends Writer{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final String basePath;
	private final long rotateAfterBytes;
	private final boolean rotationEnabled;

	private spade.reporter.audit.writer.file.File currentWriter;
	private int currentFileIndex;
	private long totalBytesWritten;

	public File(final Config config) throws Exception{
		super(config);
		this.basePath = config.getBasePath();
		this.rotateAfterBytes = config.getRotateAfterBytes();
		this.rotationEnabled = this.rotateAfterBytes > 0;
		this.currentFileIndex = 0;
		this.totalBytesWritten = 0;
		this.currentWriter = new spade.reporter.audit.writer.file.File(new spade.reporter.audit.writer.file.Config(basePath));
	}

	@Override
	public long writeEvent(final Event event) throws Exception{
		if(event == null){
			return 0;
		}

		final long bytesWritten = currentWriter.writeEvent(event);
		this.totalBytesWritten += bytesWritten;

		if(rotationEnabled && totalBytesWritten >= rotateAfterBytes){
			currentWriter.close();
			currentFileIndex++;
			totalBytesWritten = 0;
			currentWriter = new spade.reporter.audit.writer.file.File(new spade.reporter.audit.writer.file.Config(basePath + "." + currentFileIndex));
		}
		return bytesWritten;
	}

	@Override
	public void close(){
		if(currentWriter == null){
			return;
		}
		try{
			currentWriter.close();
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to close rotating file writer", e);
		}finally{
			currentWriter = null;
		}
	}
}
