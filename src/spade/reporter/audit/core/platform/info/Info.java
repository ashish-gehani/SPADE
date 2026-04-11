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
package spade.reporter.audit.core.platform.info;

import spade.reporter.audit.core.platform.ID;

public class Info {

	private final ID id;
	private final OS os;
	private final Architecture architecture;
	private final Version osVersion;
	private final KernelVersion kernelVersion;

	public Info(
		final ID id,
		final OS os,
		final Architecture architecture,
		final Version osVersion,
		final KernelVersion kernelVersion
	){
		if(id == null){
			throw new IllegalArgumentException("id cannot be NULL");
		}
		if(os == null){
			throw new IllegalArgumentException("os cannot be NULL");
		}
		if(architecture == null){
			throw new IllegalArgumentException("architecture cannot be NULL");
		}
		if(osVersion == null){
			throw new IllegalArgumentException("osVersion cannot be NULL");
		}
		if(kernelVersion == null){
			throw new IllegalArgumentException("kernelVersion cannot be NULL");
		}
		this.id = id;
		this.os = os;
		this.architecture = architecture;
		this.osVersion = osVersion;
		this.kernelVersion = kernelVersion;
	}

	public ID getId(){ return id; }
	public OS getOS(){ return os; }
	public Architecture getArchitecture(){ return architecture; }
	public Version getOSVersion(){ return osVersion; }
	public KernelVersion getKernelVersion(){ return kernelVersion; }

}
