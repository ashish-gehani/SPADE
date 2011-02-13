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

package spade.producers;

import spade.Buffer;
import spade.AbstractProducer;
import spade.opm.edge.Edge;
import spade.opm.edge.WasTriggeredBy;
import spade.opm.edge.WasGeneratedBy;
import spade.opm.edge.Used;
import spade.opm.edge.WasDerivedFrom;
import spade.opm.vertex.Artifact;
import spade.opm.vertex.Process;

public class SimpleProducer extends AbstractProducer {

    @Override
    public boolean initialize(String arguments) {
        addSomeData();
        return true;
    }

    private void addSomeData() {
        Process root = new Process();
        root.addAnnotation("name", "root process");
        root.addAnnotation("pid", "10");
        putVertex(root);

        Process child = new Process();
        child.addAnnotation("name", "child process");
        child.addAnnotation("pid", "32");
        putVertex(child);

        Edge forkedge = new WasTriggeredBy(child, root);
        forkedge.addAnnotation("time", "5:56 PM");
        putEdge(forkedge);

        Artifact file1 = new Artifact();
        file1.addAnnotation("filename", "output.tmp");
        putVertex(file1);

        Artifact file2 = new Artifact();
        file2.addAnnotation("filename", "output.o");
        putVertex(file2);

        Edge readedge = new Used(child, file1);
        readedge.addAnnotation("iotime", "12 ms");
        putEdge(readedge);

        Edge writeedge = new WasGeneratedBy(file2, child);
        writeedge.addAnnotation("iotime", "11 ms");
        putEdge(writeedge);

        Edge renameedge = new WasDerivedFrom(file2, file1);
        putEdge(renameedge);
    }

    @Override
    public boolean shutdown() {
        return true;
    }

}
