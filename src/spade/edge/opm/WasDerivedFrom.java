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
package spade.edge.opm;

import spade.core.AbstractEdge;
import spade.vertex.opm.Artifact;

/**
 * WasDerivedFrom edge based on the OPM model.
 *
 * @author Dawood Tariq
 */
public class WasDerivedFrom extends AbstractEdge{

	private static final long serialVersionUID = -6856260120198042435L;

	public static final String typeValue = "WasDerivedFrom";

	/**
	 * Constructor for Artifact->Artifact edge
	 *
	 * @param sourceArtifact      Source artifact vertex
	 * @param destinationArtifact Destination artifact vertex
	 */
	public WasDerivedFrom(Artifact sourceArtifact, Artifact destinationArtifact){
		super();
		setChildVertex(sourceArtifact);
		setParentVertex(destinationArtifact);
		setType(typeValue);
	}

	public WasDerivedFrom(final String bigHashCode, Artifact sourceArtifact, Artifact destinationArtifact){
		super(bigHashCode);
		setChildVertex(sourceArtifact);
		setParentVertex(destinationArtifact);
		setType(typeValue);
	}
}
