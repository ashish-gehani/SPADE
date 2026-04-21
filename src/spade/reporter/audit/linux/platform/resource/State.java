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

public class State extends spade.reporter.audit.core.platform.resource.State<ID>{

	private static final long STARTING_VERSION = 0;
	private static final long STARTING_EPOCH = 0;

	private long version = STARTING_VERSION;
	private long epoch = STARTING_EPOCH;

	public State(final ID id){
		super(id);
	}

	public long getVersion(){
		return version;
	}

	public void incrementVersion(){
		this.version++;
	}

	public long getEpoch(){
		return epoch;
	}

	public void incrementEpoch(){
		this.epoch++;
		this.version = STARTING_VERSION;
	}

}
