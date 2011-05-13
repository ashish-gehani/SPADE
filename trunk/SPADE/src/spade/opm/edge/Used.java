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
import spade.opm.vertex.Process;
import java.util.LinkedHashMap;
import java.util.Map;

public class Used extends AbstractEdge {

    public Used(Process actingProcess, Artifact usedArtifact) {
        setSourceVertex(actingProcess);
        setDestinationVertex(usedArtifact);
        annotations = new LinkedHashMap<String, String>();
        addAnnotation("type", "Used");
    }

    public Used(Process actingProcess, Artifact usedArtifact, Map<String, String> inputAnnotations) {
        setSourceVertex(actingProcess);
        setDestinationVertex(usedArtifact);
        setAnnotations(inputAnnotations);
        addAnnotation("type", "Used");
    }
}
