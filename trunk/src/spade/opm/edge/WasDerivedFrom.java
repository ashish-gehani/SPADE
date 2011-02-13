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

import spade.opm.vertex.Artifact;
import spade.opm.vertex.Vertex;
import java.util.LinkedHashMap;

public class WasDerivedFrom extends Edge {

    private Artifact sourceArtifact;
    private Artifact destinationArtifact;

    public WasDerivedFrom(Artifact srcArtifact, Artifact dstArtifact) {
        sourceArtifact = srcArtifact;
        destinationArtifact = dstArtifact;
        edgeType = "WasDerivedFrom";
        annotations = new LinkedHashMap<String, String>();
    }

    public Artifact getSourceArtifact() {
        return sourceArtifact;
    }

    public void setSourceArtifact(Artifact artifact1) {
        this.sourceArtifact = artifact1;
    }

    public Artifact getDestinationArtifact() {
        return destinationArtifact;
    }

    public void setDestinationArtifact(Artifact artifact2) {
        this.destinationArtifact = artifact2;
    }

    @Override
    public Vertex getSrcVertex() {
        return sourceArtifact;
    }

    @Override
    public Vertex getDstVertex() {
        return destinationArtifact;
    }
}
