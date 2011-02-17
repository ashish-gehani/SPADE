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

package spade.producer;

import spade.core.AbstractProducer;
import spade.core.AbstractEdge;
import spade.opm.edge.WasTriggeredBy;
import spade.opm.edge.WasGeneratedBy;
import spade.opm.edge.Used;
import spade.opm.edge.WasControlledBy;
import spade.opm.edge.WasDerivedFrom;
import spade.opm.vertex.Agent;
import spade.opm.vertex.Artifact;
import spade.opm.vertex.Process;

public class SimpleProducer extends AbstractProducer {

    @Override
    public boolean launch(String arguments) {
        // Add data and return true to indicate that the
        // producer was launched successfully.
        addSomeData();
        return true;
    }

    private void addSomeData() {
        // Create a root process vertex and pass it
        // to the kernel using the putVertex() method
        Process root = new Process();
        root.addAnnotation("name", "root process");
        root.addAnnotation("pid", "-10");
        putVertex(root);

        // Create a child process vertex
        Process child = new Process();
        child.addAnnotation("name", "child process");
        child.addAnnotation("pid", "32");
        putVertex(child);

        // Create an edge between the root and child process
        // vertices. Edges are directed where the first
        // argument in the constructor is the source and the
        // second argument is the destination (e.g., this
        // edge can be read as "child _WasTriggeredBy_ root")
        AbstractEdge forkedge = new WasTriggeredBy(child, root);
        forkedge.addAnnotation("time", "5:56 PM");
        putEdge(forkedge);

        Artifact file1 = new Artifact();
        file1.addAnnotation("filename", "output.tmp");
        putVertex(file1);

        Artifact file2 = new Artifact();
        file2.addAnnotation("filename", "output.o");
        putVertex(file2);

        AbstractEdge readedge = new Used(child, file1);
        readedge.addAnnotation("iotime", "12 ms");
        putEdge(readedge);

        AbstractEdge writeedge = new WasGeneratedBy(file2, child);
        writeedge.addAnnotation("iotime", "11 ms");
        putEdge(writeedge);

        AbstractEdge renameedge = new WasDerivedFrom(file2, file1);
        putEdge(renameedge);

        Agent user = new Agent();
        user.addAnnotation("uid", "10");
        user.addAnnotation("gid", "10");
        user.addAnnotation("name", "john");
        putVertex(user);

        WasControlledBy childControlledEdge = new WasControlledBy(child, user);
        putEdge(childControlledEdge);

        WasControlledBy rootControlledEdge = new WasControlledBy(root, user);
        putEdge(rootControlledEdge);
    }

    @Override
    public boolean shutdown() {
        return true;
    }

}
