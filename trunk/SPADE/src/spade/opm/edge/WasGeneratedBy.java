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

public class WasGeneratedBy extends AbstractEdge {

    private Artifact generatedArtifact;
    private Process actingProcess;

    public WasGeneratedBy(Artifact generatedArtifact, Process actingProcess) {
        this.actingProcess = actingProcess;
        this.generatedArtifact = generatedArtifact;
        annotations = new LinkedHashMap<String, String>();
        this.addAnnotation("type", "WasGeneratedBy");
    }

    public WasGeneratedBy(Artifact generatedArtifact, Process actingProcess, Map<String, String> inputAnnotations) {
        this.actingProcess = actingProcess;
        this.generatedArtifact = generatedArtifact;
        this.setAnnotations(inputAnnotations);
        this.addAnnotation("type", "WasGeneratedBy");
    }

    public Artifact getArtifact() {
        return generatedArtifact;
    }

    public Process getProcess() {
        return actingProcess;
    }

    public void setArtifact(Artifact generatedArtifact) {
        this.generatedArtifact = generatedArtifact;
    }

    public void setProcess(Process actingProcess) {
        this.actingProcess = actingProcess;
    }

    @Override
    public AbstractVertex getSrcVertex() {
        return generatedArtifact;
    }

    @Override
    public AbstractVertex getDstVertex() {
        return actingProcess;
    }
}
