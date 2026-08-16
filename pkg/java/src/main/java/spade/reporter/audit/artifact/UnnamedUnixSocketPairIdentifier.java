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

import java.util.Map;

import spade.reporter.audit.OPMConstants;

public class UnnamedUnixSocketPairIdentifier extends FdPairIdentifier{

	private static final long serialVersionUID = 9009374043657988074L;

	public UnnamedUnixSocketPairIdentifier(String tgid, String fd0, String fd1){
		super(tgid, fd0, fd1);
	}
	
	@Override
	public Map<String, String> getAnnotationsMap(){
		Map<String, String> map = super.getAnnotationsMap();
		map.put(OPMConstants.ARTIFACT_FD0, String.valueOf(fd0));
		map.put(OPMConstants.ARTIFACT_FD1, String.valueOf(fd1));
		return map;
	}

	@Override
	public String getSubtype() {
		return OPMConstants.SUBTYPE_UNNAMED_UNIX_SOCKET_PAIR;
	}
	
	@Override
	public String toString() {
		return "UnnamedUnixSocketPairIdentifier [tgid=" + tgid + ", fd0=" + fd0 + ", fd1=" + fd1 + "]";
	}

}
