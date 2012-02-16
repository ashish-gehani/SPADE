/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2011 SRI International

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
import spade.vertex.opm.Process;

/**
 * WasGeneratedBy edge based on the OPM model.
 *
 * @author Dawood
 */
public class WasGeneratedBy extends AbstractEdge {

    /**
     * Constructor for Artifact->Process edge
     *
     * @param generatedArtifact Artifact vertex
     * @param actingProcess Process vertex
     */
    public WasGeneratedBy(Artifact generatedArtifact, Process actingProcess) {
        setSourceVertex(generatedArtifact);
        setDestinationVertex(actingProcess);
        addAnnotation("type", "WasGeneratedBy");
    }
}
