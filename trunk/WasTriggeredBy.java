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

import java.util.HashMap;

public class WasTriggeredBy extends Edge {

    private Process process1;
    private Process process2;

    public WasTriggeredBy(Process inputProcess1, Process inputProcess2) {
        process1 = inputProcess1;
        process2 = inputProcess2;
        role = "WasTriggeredBy";
        edgeType = "WasTriggeredBy";
        annotations = new HashMap<String, String>();
    }

    public WasTriggeredBy(Process inputProcess1, Process inputProcess2, String inputRole, String inputEdgeType, HashMap<String, String> inputAnnotations) {
        process1 = inputProcess1;
        process2 = inputProcess2;
        role = inputRole;
        edgeType = inputEdgeType;
        annotations = inputAnnotations;
    }

    public WasTriggeredBy(Process inputProcess1, Process inputProcess2, String inputRole, String inputEdgeType) {
        process1 = inputProcess1;
        process2 = inputProcess2;
        role = inputRole;
        edgeType = inputEdgeType;
        annotations = new HashMap<String, String>();
    }

    public Process getProcess1() {
        return process1;
    }

    public void setProcess1(Process process1) {
        this.process1 = process1;
    }

    public Process getProcess2() {
        return process2;
    }

    public void setProcess2(Process process2) {
        this.process2 = process2;
    }

    @Override
    public Vertex getSrcVertex() {
        return process1;
    }

    @Override
    public Vertex getDstVertex() {
        return process2;
    }
}
