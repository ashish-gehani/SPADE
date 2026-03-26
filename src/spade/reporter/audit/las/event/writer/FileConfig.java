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

/**
 * Configuration for File Writer.
 *
 * Encapsulates file path, rotation threshold, and verbose flag.
 */
public class FileConfig{

	private final String filePath;
	private final long rotateAfterEstimatedBytes;
	private final boolean verbose;

	/**
	 * @param filePath the output file path
	 * @param rotateAfterEstimatedBytes byte threshold for rotation (0 = no rotation)
	 * @param verbose enable verbose logging
	 */
	public FileConfig(final String filePath, final long rotateAfterEstimatedBytes,
			final boolean verbose){
		this.filePath = filePath;
		this.rotateAfterEstimatedBytes = rotateAfterEstimatedBytes < 1 ? 0 : rotateAfterEstimatedBytes;
		this.verbose = verbose;
	}

	public String getFilePath(){
		return filePath;
	}

	public long getRotateAfterEstimatedBytes(){
		return rotateAfterEstimatedBytes;
	}

	public boolean isVerbose(){
		return verbose;
	}

	public boolean isRotationEnabled(){
		return rotateAfterEstimatedBytes > 0;
	}
}
