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

public class WasControlledBy extends Edge {

    private Process process;
    private Agent agent;

    public WasControlledBy(Process inputProcess, Agent inputAgent, String inputRole, String inputEdgeType, HashMap<String, String> inputAnnotations) {
        process = inputProcess;
        agent = inputAgent;
        role = inputRole;
        edgeType = inputEdgeType;
        annotations = inputAnnotations;
    }

    public WasControlledBy(Process inputProcess, Agent inputAgent, String inputRole, String inputEdgeType) {
        process = inputProcess;
        agent = inputAgent;
        role = inputRole;
        edgeType = inputEdgeType;
        annotations = new HashMap<String, String>();
    }

    public Process getProcess() {
        return process;
    }

    public Agent getAgent() {
        return agent;
    }

    public void setProcess(Process process) {
        this.process = process;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    @Override
    public Vertex getSrcVertex() {
        return process;
    }

    @Override
    public Vertex getDstVertex() {
        return agent;
    }
}
