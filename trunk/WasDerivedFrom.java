/*
--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2010 SRI International

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

import java.util.HashMap;

public class WasDerivedFrom extends Edge {

    private Artifact artifact1;
    private Artifact artifact2;

    public WasDerivedFrom(Artifact inputArtifact1, Artifact inputArtifact2, String inputRole, String inputEdgeType, HashMap<String, String> inputAnnotations) {
        artifact1 = inputArtifact1;
        artifact2 = inputArtifact2;
        role = inputRole;
        edgeType = inputEdgeType;
        annotations = inputAnnotations;
    }

    public WasDerivedFrom(Artifact inputArtifact1, Artifact inputArtifact2, String inputRole, String inputEdgeType) {
        artifact1 = inputArtifact1;
        artifact2 = inputArtifact2;
        role = inputRole;
        edgeType = inputEdgeType;
        annotations = new HashMap<String, String>();
    }

    public Artifact getArtifact1() {
        return artifact1;
    }

    public void setArtifact1(Artifact artifact1) {
        this.artifact1 = artifact1;
    }

    public Artifact getArtifact2() {
        return artifact2;
    }

    public void setArtifact2(Artifact artifact2) {
        this.artifact2 = artifact2;
    }
}
