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
import spade.opm.vertex.Process;
import java.util.LinkedHashMap;
import java.util.Map;

public class Used extends AbstractEdge {

    private Process actingProcess;
    private Artifact usedArtifact;

    public Used(Process actingProcess, Artifact usedArtifact) {
        this.actingProcess = actingProcess;
        this.usedArtifact = usedArtifact;
        edgeType = "Used";
        annotations = new LinkedHashMap<String, String>();
        this.addAnnotation("type", edgeType);
    }

    public Used(Process actingProcess, Artifact usedArtifact, Map<String, String> inputAnnotations) {
        this.actingProcess = actingProcess;
        this.usedArtifact = usedArtifact;
        edgeType = "Used";
        this.setAnnotations(inputAnnotations);
        this.addAnnotation("type", edgeType);
    }

    public Process getProcess() {
        return actingProcess;
    }

    public Artifact getArtifact() {
        return usedArtifact;
    }

    public void setProcess(Process actingProcess) {
        this.actingProcess = actingProcess;
    }

    public void setArtifact(Artifact usedArtifact) {
        this.usedArtifact = usedArtifact;
    }

    @Override
    public AbstractVertex getSrcVertex() {
        return actingProcess;
    }

    @Override
    public AbstractVertex getDstVertex() {
        return usedArtifact;
    }
}
