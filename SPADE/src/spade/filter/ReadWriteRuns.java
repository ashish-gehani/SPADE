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

package spade.filter;

import spade.opm.edge.WasGeneratedBy;
import spade.opm.edge.Used;
import java.util.HashMap;

public class ReadWriteRuns {

    int currentRun;
    //currentRun = 0 -> read run
    //currentRun = 1 -> write run
    private HashMap<String, Used> readRuns;
    private HashMap<String, WasGeneratedBy> writeRuns;
    //key is pid->edge

    public ReadWriteRuns() {
        readRuns = new HashMap<String, Used>();
        writeRuns = new HashMap<String, WasGeneratedBy>();
        currentRun = -1;
    }

    public int getCurrentRun() {
        return currentRun;
    }

    public void setCurrentRun(int currentRun) {
        this.currentRun = currentRun;
    }

    public HashMap<String, Used> getReadRuns() {
        return readRuns;
    }

    public void setReadRuns(HashMap<String, Used> readRuns) {
        this.readRuns = readRuns;
    }

    public HashMap<String, WasGeneratedBy> getWriteRuns() {
        return writeRuns;
    }

    public void setWriteRuns(HashMap<String, WasGeneratedBy> writeRuns) {
        this.writeRuns = writeRuns;
    }
}
