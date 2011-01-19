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

public interface FilterInterface {

    public boolean putVertex(Agent a);

    public boolean putVertex(Process p);

    public boolean putVertex(Artifact a);

    public boolean putEdge(Used u);

    public boolean putEdge(WasControlledBy wcb);

    public boolean putEdge(WasDerivedFrom wdf);

    public boolean putEdge(WasGeneratedBy wgb);

    public boolean putEdge(WasTriggeredBy wtb);
}
