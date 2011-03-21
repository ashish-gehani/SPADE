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
import spade.core.AbstractVertex;
import spade.opm.vertex.Process;
import java.util.LinkedHashMap;
import java.util.Map;

public class WasTriggeredBy extends AbstractEdge {

    private Process triggeredProcess;
    private Process callingProcess;

    public WasTriggeredBy(Process triggeredProcess, Process callingProcess) {
        this.triggeredProcess = triggeredProcess;
        this.callingProcess = callingProcess;
        edgeType = "WasTriggeredBy";
        annotations = new LinkedHashMap<String, String>();
        this.addAnnotation("type", edgeType);
    }

    public WasTriggeredBy(Process triggeredProcess, Process callingProcess, Map<String, String> inputAnnotations) {
        this.triggeredProcess = triggeredProcess;
        this.callingProcess = callingProcess;
        edgeType = "WasTriggeredBy";
        this.setAnnotations(inputAnnotations);
        this.addAnnotation("type", edgeType);
    }

    public Process getCalledProcess() {
        return triggeredProcess;
    }

    public void setCalledProcess(Process triggeredProcess) {
        this.triggeredProcess = triggeredProcess;
    }

    public Process getCallingProcess() {
        return callingProcess;
    }

    public void setCallingProcess(Process callingProcess) {
        this.callingProcess = callingProcess;
    }

    @Override
    public AbstractVertex getSrcVertex() {
        return triggeredProcess;
    }

    @Override
    public AbstractVertex getDstVertex() {
        return callingProcess;
    }
}
