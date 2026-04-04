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
package spade.reporter.audit.linux.process.file.descriptor;

import spade.reporter.audit.linux.process.file.descriptor.type.Descriptor;

public class State extends spade.reporter.audit.core.util.statetable.State<Num>{

	private final OpenMode openMode;
	private final Descriptor descriptor;

	public State(final Num num, final OpenMode openMode, final Descriptor descriptor){
		super(num);
		if(openMode == null){
			throw new IllegalArgumentException("openState cannot be NULL");
		}
		if(descriptor == null){
			throw new IllegalArgumentException("descriptor cannot be NULL");
		}
		this.openMode = openMode;
		this.descriptor = descriptor;
	}

	public OpenMode getOpenState(){
		return openMode;
	}

	public Descriptor getDescriptor(){
		return descriptor;
	}

}
