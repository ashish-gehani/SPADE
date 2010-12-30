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
public interface Filter {
	
	// this is a filter interface. Any filter needs to implement these functions as these are the put calls to the storage on which
	// the filter must intercede and do its own processing
	
	
	public void putVertex(Agent a);
	public void putVertex(Process p);
	public void putVertex(Artifact a);
	
	public void putEdge(Vertex v1, Vertex v2, Used u);
	public void putEdge(Vertex v1, Vertex v2, WasControlledBy wcb);
	public void putEdge(Vertex v1, Vertex v2, WasDerivedFrom wdf);
	public void putEdge(Vertex v1, Vertex v2, WasGeneratedBy wgb);
	public void putEdge(Vertex v1, Vertex v2, WasTriggeredBy wtb);
	

}
