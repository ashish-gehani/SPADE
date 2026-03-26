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

public class FileReaderConfig extends Config{

	private final String filePath;

	public FileReaderConfig(
		final String filePath, final boolean verboseEventFactory
	){
		super(Type.File, verboseEventFactory);
		if(filePath == null){
			throw new IllegalArgumentException("File path cannot be NULL");
		}
		this.filePath = filePath;
	}

	public String getFilePath(){
		return filePath;
	}
}
