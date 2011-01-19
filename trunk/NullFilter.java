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

public class NullFilter implements FilterInterface {

    private StorageInterface storage;

    public NullFilter(StorageInterface inputStorage) {
        storage = inputStorage;
    }

    @Override
    public boolean putVertex(Agent a) {
        return storage.putVertex(a);
    }

    @Override
    public boolean putVertex(Process p) {
        return storage.putVertex(p);
    }

    @Override
    public boolean putVertex(Artifact a) {
        return storage.putVertex(a);
    }

    @Override
    public boolean putEdge(Used u) {
        return storage.putEdge(u);
    }

    @Override
    public boolean putEdge(WasControlledBy wcb) {
        return storage.putEdge(wcb);
    }

    @Override
    public boolean putEdge(WasDerivedFrom wdf) {
        return storage.putEdge(wdf);
    }

    @Override
    public boolean putEdge(WasGeneratedBy wgb) {
        return storage.putEdge(wgb);
    }

    @Override
    public boolean putEdge(WasTriggeredBy wtb) {
        return storage.putEdge(wtb);
    }
}
