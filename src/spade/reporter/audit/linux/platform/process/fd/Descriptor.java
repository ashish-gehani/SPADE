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
package spade.reporter.audit.linux.platform.process.fd;

import spade.reporter.audit.core.util.statetable.State;
import spade.reporter.audit.linux.platform.resource.Resource;
import spade.reporter.audit.linux.type.fd.Num;

public class Descriptor extends State<Num>{

	public final Type type;
	private final Resource resource;
	private final OpenMode openMode;

	public Descriptor(final Descriptor other){
		this(other.type, new Num(other.getId()), other.openMode, other.resource);
	}

	public Descriptor(final Type type, final Num num, final OpenMode openMode, final Resource resource){
		super(num);
		if(type == null){
			throw new IllegalArgumentException("type cannot be NULL");
		}
		if(resource == null){
			throw new IllegalArgumentException("resource cannot be NULL");
		}
		if(openMode == null){
			throw new IllegalArgumentException("openMode cannot be NULL");
		}
		Validate.validate(type, resource);
		this.type = type;
		this.resource = resource;
		this.openMode = openMode;
	}

	public Resource getResource(){
		return resource;
	}

	public OpenMode getOpenMode(){
		return openMode;
	}

}
