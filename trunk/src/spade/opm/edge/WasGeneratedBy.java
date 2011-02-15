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
import spade.opm.vertex.Process;
import java.util.LinkedHashMap;
import java.util.Map;

public class WasGeneratedBy extends Edge {

    private Artifact generatedArtifact;
    private Process actingProcess;

    public WasGeneratedBy(Artifact generatedArtifact, Process actingProcess) {
        this.actingProcess = actingProcess;
        this.generatedArtifact = generatedArtifact;
        edgeType = "WasGeneratedBy";
        annotations = new LinkedHashMap<String, String>();
    }

    public WasGeneratedBy(Artifact generatedArtifact, Process actingProcess, Map<String, String> inputAnnotations) {
        this.actingProcess = actingProcess;
        this.generatedArtifact = generatedArtifact;
        edgeType = "WasGeneratedBy";
        this.setAnnotations(inputAnnotations);
    }

    public Artifact getArtifact() {
        return generatedArtifact;
    }

    public Process getProcess() {
        return actingProcess;
    }

    public void setArtifact(Artifact artifact) {
        this.generatedArtifact = artifact;
    }

    public void setProcess(Process process) {
        this.actingProcess = process;
    }

    @Override
    public Vertex getSrcVertex() {
        return generatedArtifact;
    }

    @Override
    public Vertex getDstVertex() {
        return actingProcess;
    }
}
