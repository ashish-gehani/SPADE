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
package spade.reporter.audit.linux.provenance.event;

import spade.reporter.audit.core.provenance.event.ID;
import spade.reporter.audit.linux.provenance.PlatformProcess;
import spade.reporter.audit.linux.provenance.SourceEvent;

public class ProcessControl extends Event{

	private final PlatformProcess controller;
	private final PlatformProcess target;

	public ProcessControl(final ID id, final SourceEvent sourceEvent, final PlatformProcess controller, final PlatformProcess target){
		super(Type.PROCESS_CONTROL, id, sourceEvent);
		if(controller == null){
			throw new IllegalArgumentException("controller cannot be NULL");
		}
		if(target == null){
			throw new IllegalArgumentException("target cannot be NULL");
		}
		this.controller = controller;
		this.target = target;
	}

	public PlatformProcess getController(){
		return controller;
	}

	public PlatformProcess getTarget(){
		return target;
	}
}
