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
package spade.reporter.procmon;

import java.io.Closeable;
import java.io.IOException;

import spade.utility.HelperFunctions;

public abstract class EventReader implements Closeable{

	public final String filePath;

	public EventReader(final String filePath){
		this.filePath = filePath;
	}

	public abstract Event read() throws Exception;
	public abstract void close() throws IOException;

	public static EventReader createReader(final String filePath) throws Exception{
		if(HelperFunctions.isNullOrEmpty(filePath)){
			throw new Exception("NULL/Empty file path to create ProcMon event reader for");
		}
		if(filePath.trim().toLowerCase().endsWith(".csv")){
			return new CSVEventReader(filePath);
		}else if(filePath.trim().toLowerCase().endsWith(".xml")){
			return new XMLEventReader(filePath);
		}else{
			throw new Exception("Unsupported ProcMon input file format. Must have extensions: ['csv' or 'xml']");
		}
	}

}
