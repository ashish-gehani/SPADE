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

import java.util.Map;
import java.util.LinkedHashMap;

public class Used extends Edge {

    private Process process;
    private Artifact artifact;

    public Used(Process inputProcess, Artifact inputArtifact) {
        process = inputProcess;
        artifact = inputArtifact;
        role = "Used";
        edgeType = "Used";
        annotations = new LinkedHashMap<String, String>();
    }

    public Used(Process inputProcess, Artifact inputArtifact, String inputRole, String inputEdgeType, Map<String, String> inputAnnotations) {
        process = inputProcess;
        artifact = inputArtifact;
        role = inputRole;
        edgeType = inputEdgeType;
        annotations = inputAnnotations;
    }

    public Used(Process inputProcess, Artifact inputArtifact, String inputRole, String inputEdgeType) {
        process = inputProcess;
        artifact = inputArtifact;
        role = inputRole;
        edgeType = inputEdgeType;
        annotations = new LinkedHashMap<String, String>();
    }

    public Process getProcess() {
        return process;
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public void setProcess(Process process) {
        this.process = process;
    }

    public void setArtifact(Artifact artifact) {
        this.artifact = artifact;
    }

    @Override
    public Vertex getSrcVertex() {
        return process;
    }

    @Override
    public Vertex getDstVertex() {
        return artifact;
    }
}
