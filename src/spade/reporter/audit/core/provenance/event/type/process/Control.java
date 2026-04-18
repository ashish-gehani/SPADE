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
package spade.reporter.audit.core.provenance.event.type.process;

import spade.reporter.audit.core.provenance.Process;
import spade.reporter.audit.core.provenance.event.Event;
import spade.reporter.audit.core.provenance.event.ID;
import spade.reporter.audit.core.provenance.event.Type;

public abstract class Control extends Event{

	private final Process controller;
	private final Process target;

	public Control(
		final ID id,
		final Process controller,
		final Process target
	){
		super(Type.PROCESS_CONTROL, id);
		if(controller == null){
			throw new IllegalArgumentException("controller cannot be NULL");
		}
		if(target == null){
			throw new IllegalArgumentException("target cannot be NULL");
		}
		this.controller = controller;
		this.target = target;
	}

	public Process getController(){
		return controller;
	}

	public Process getTarget(){
		return target;
	}

}
