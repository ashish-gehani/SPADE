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

public class UnnamedPipeIdentifier extends FdPairIdentifier{

	private static final long serialVersionUID = -4888235272900911375L;
	
	/**
	 * @param tgid owner process's thread group id
	 * @param fd0 read end of the pipe
	 * @param fd1 write end of the pipe
	 */
	public UnnamedPipeIdentifier(String tgid, String fd0, String fd1){
		super(tgid, fd0, fd1);
	}

	public String getSubtype(){
		return OPMConstants.SUBTYPE_UNNAMED_PIPE;
	}
	
	@Override
	public Map<String, String> getAnnotationsMap() {
		Map<String, String> annotations = super.getAnnotationsMap();
		annotations.put(OPMConstants.ARTIFACT_READ_FD, String.valueOf(fd0));
		annotations.put(OPMConstants.ARTIFACT_WRITE_FD, String.valueOf(fd1));
		return annotations;
	}

	@Override
	public String toString() {
		return "UnnamedPipeIdentifier [fd0=" + fd0 + ", fd1=" + fd1 + ", tgid=" + tgid + "]";
	}
}
