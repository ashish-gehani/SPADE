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

import spade.opm.vertex.Vertex;
import spade.opm.vertex.Process;
import java.util.LinkedHashMap;

public class WasTriggeredBy extends Edge {

    private Process calledProcess;
    private Process callingProcess;

    public WasTriggeredBy(Process callee, Process caller) {
        calledProcess = callee;
        callingProcess = caller;
        edgeType = "WasTriggeredBy";
        annotations = new LinkedHashMap<String, String>();
    }

    public Process getCalledProcess() {
        return calledProcess;
    }

    public void setCalledProcess(Process process1) {
        this.calledProcess = process1;
    }

    public Process getCallingProcess() {
        return callingProcess;
    }

    public void setCallingProcess(Process process2) {
        this.callingProcess = process2;
    }

    @Override
    public Vertex getSrcVertex() {
        return calledProcess;
    }

    @Override
    public Vertex getDstVertex() {
        return callingProcess;
    }
}
