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
package spade.opm.edge;

import spade.core.AbstractEdge;
import spade.opm.vertex.Artifact;
import spade.core.AbstractVertex;
import java.util.LinkedHashMap;
import java.util.Map;

public class WasDerivedFrom extends AbstractEdge {

    private Artifact sourceArtifact;
    private Artifact destinationArtifact;

    public WasDerivedFrom(Artifact sourceArtifact, Artifact destinationArtifact) {
        this.sourceArtifact = sourceArtifact;
        this.destinationArtifact = destinationArtifact;
        edgeType = "WasDerivedFrom";
        annotations = new LinkedHashMap<String, String>();
        this.addAnnotation("type", edgeType);
    }

    public WasDerivedFrom(Artifact sourceArtifact, Artifact destinationArtifact, Map<String, String> inputAnnotations) {
        this.sourceArtifact = sourceArtifact;
        this.destinationArtifact = destinationArtifact;
        edgeType = "WasDerivedFrom";
        this.setAnnotations(inputAnnotations);
        this.addAnnotation("type", edgeType);
    }

    public Artifact getSourceArtifact() {
        return sourceArtifact;
    }

    public void setSourceArtifact(Artifact sourceArtifact) {
        this.sourceArtifact = sourceArtifact;
    }

    public Artifact getDestinationArtifact() {
        return destinationArtifact;
    }

    public void setDestinationArtifact(Artifact destinationArtifact) {
        this.destinationArtifact = destinationArtifact;
    }

    @Override
    public AbstractVertex getSrcVertex() {
        return sourceArtifact;
    }

    @Override
    public AbstractVertex getDstVertex() {
        return destinationArtifact;
    }
}
