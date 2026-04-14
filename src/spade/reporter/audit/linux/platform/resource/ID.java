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
package spade.reporter.audit.linux.platform.resource;

import spade.reporter.audit.core.util.statetable.Indexable;
import spade.reporter.audit.linux.platform.process.State;

public abstract class ID implements Indexable<ID>{

	private final Resource resource;
	private final State processState;

	public ID(final Resource resource, final State processState){
		if(resource == null){
			throw new IllegalArgumentException("resource cannot be NULL");
		}
		if(processState == null){
			throw new IllegalArgumentException("processState cannot be NULL");
		}
		this.resource = resource;
		this.processState = processState;
	}

	public Resource getResource(){
		return resource;
	}

	public State getProcessState(){
		return processState;
	}

}
