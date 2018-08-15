/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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
package spade.reporter.audit.artifact;

public class ArtifactConfig{

	public final boolean output;
	public final boolean hasEpoch, hasVersion, hasPermissions;
	public final boolean canBeCreated, canBeVersioned, canBePermissioned;
	
	public ArtifactConfig(boolean output, boolean hasEpoch, boolean hasVersion, boolean hasPermissions,
			boolean canBeCreated, boolean canBeVersioned, boolean canBePermissioned){
		this.output = output;
		this.hasEpoch = hasEpoch;
		this.hasVersion = hasVersion;
		this.hasPermissions = hasPermissions;
		this.canBeCreated = canBeCreated;
		this.canBeVersioned = canBeVersioned;
		this.canBePermissioned = canBePermissioned;
	}
	
}
