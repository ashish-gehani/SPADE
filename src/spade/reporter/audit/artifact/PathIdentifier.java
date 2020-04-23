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

import java.util.HashMap;
import java.util.Map;

import spade.reporter.audit.LinuxPathResolver;
import spade.reporter.audit.OPMConstants;

/**
 * Just a utility class. Added because FileIdentity, NamePipeIdentity and UnixSocketIdentity all have the same implementation.
 * So they all extend this class
 */

public abstract class PathIdentifier extends ArtifactIdentifier{
	
	private static final long serialVersionUID = 2336830245458423080L;
	
	public final String path;
	public final String rootFSPath;
	
	public final String combinedPath;
	
	public PathIdentifier(String path, String rootFSPath){
		this.path = path;
		this.rootFSPath = rootFSPath;
		this.combinedPath = LinuxPathResolver.joinPaths(path, rootFSPath, null);
	}
	
	@Override
	public Map<String, String> getAnnotationsMap(){
		Map<String, String> annotations = new HashMap<String, String>();
		annotations.put(OPMConstants.ARTIFACT_PATH, path);
		if(!"/".equals(rootFSPath)){
			annotations.put(OPMConstants.ARTIFACT_ROOT_PATH, rootFSPath);
		}
//		annotations.put(OPMConstants.ARTIFACT_PATH, combinedPath);
		return annotations;
	}

	public abstract String getSubtype();
	
	@Override
	public int hashCode(){
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((combinedPath == null) ? 0 : combinedPath.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj){
		if(this == obj)
			return true;
		if(!super.equals(obj))
			return false;
		if(getClass() != obj.getClass())
			return false;
		PathIdentifier other = (PathIdentifier)obj;
		if(combinedPath == null){
			if(other.combinedPath != null)
				return false;
		}else if(!combinedPath.equals(other.combinedPath))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + " [combinedPath=" + combinedPath + "]";
	}
}
