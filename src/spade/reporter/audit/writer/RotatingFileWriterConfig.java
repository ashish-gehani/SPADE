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
package spade.reporter.audit.writer;

public class RotatingFileWriterConfig extends Config{

	private final String basePath;
	private final long rotateAfterBytes;

	public RotatingFileWriterConfig(final String basePath, final long rotateAfterBytes){
		super(Type.ROTATING_FILE);
		if(basePath == null){
			throw new IllegalArgumentException("Base path cannot be NULL");
		}
		this.basePath = basePath;
		this.rotateAfterBytes = rotateAfterBytes;
	}

	public String getBasePath(){
		return basePath;
	}

	public long getRotateAfterBytes(){
		return rotateAfterBytes;
	}
}
