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
package spade.reporter;

import spade.core.AbstractReporter;
import spade.core.AbstractEdge;
import spade.opm.edge.WasTriggeredBy;
import spade.opm.edge.WasGeneratedBy;
import spade.opm.edge.Used;
import spade.opm.edge.WasControlledBy;
import spade.opm.edge.WasDerivedFrom;
import spade.opm.vertex.Agent;
import spade.opm.vertex.Artifact;
import spade.opm.vertex.Process;

public class SimpleReporter extends AbstractReporter {

    @Override
    public boolean launch(String arguments) {
        // Add data and return true to indicate that the
        // producer was launched successfully.
        addSomeData();
        return true;
    }

    private void addSomeData() {
        Artifact a1 = new Artifact();
        a1.addAnnotation("filename", "remote_httpd.log");
        a1.addAnnotation("path", "/var/log/remote_httpd.log");
        a1.addAnnotation("size", "44182");
        a1.addAnnotation("lastmodified", "Wed Dec 7 13:48:26 2011");
        
        Process p1 = new Process();
        p1.addAnnotation("pidname", "cat");
        p1.addAnnotation("pid", "1364");
        p1.addAnnotation("uid", "501");
        p1.addAnnotation("starttime", "Fri Dec 9 9:15:02 2011");
        putVertex(p1);

        Process p2 = new Process();
        p2.addAnnotation("pidname", "sshd");
        p2.addAnnotation("pid", "1422");
        p2.addAnnotation("uid", "501");
        p2.addAnnotation("starttime", "Fri Dec 9 9:14:57 2011");
        putVertex(p2);

        Artifact n1 = new Artifact();
        n1.addAnnotation("local_host", "10.12.0.55");
        n1.addAnnotation("local_port", "22");
        n1.addAnnotation("remote_host", "10.12.0.34");
        n1.addAnnotation("remote_port", "1359");

        Artifact n2 = new Artifact();
        n2.addAnnotation("local_host", "10.12.0.34");
        n2.addAnnotation("local_port", "1359");
        n2.addAnnotation("remote_host", "10.12.0.55");
        n2.addAnnotation("remote_port", "22");

        Process p3 = new Process();
        p3.addAnnotation("pidname", "ssh");
        p3.addAnnotation("pid", "3106");
        p3.addAnnotation("uid", "10");
        p3.addAnnotation("starttime", "Fri Dec 9 9:14:15 2011");
        putVertex(p3);

        Process p4 = new Process();
        p4.addAnnotation("pidname", "tcsh");
        p4.addAnnotation("pid", "3059");
        p4.addAnnotation("uid", "10");
        p4.addAnnotation("starttime", "Fri Dec 9 9:14:11 2011");
        putVertex(p4);
        
        Artifact a2 = new Artifact();
        a2.addAnnotation("filename", "local_httpd.log");
        a2.addAnnotation("path", "/tmp/local_httpd.log");
        a2.addAnnotation("size", "44182");
        a2.addAnnotation("lastmodified", "Wed Dec 7 13:48:26 2011");
        
        
        WasGeneratedBy wgb1 = new WasGeneratedBy(a2,p4);
        WasTriggeredBy wtb1 = new WasTriggeredBy(p4,p3);
        Used used1 = new Used(p3,n2);
        WasGeneratedBy wgb2 = new WasGeneratedBy(n1,p2);
        WasTriggeredBy wtb2 = new WasTriggeredBy(p2,p1);
        Used used2 = new Used(p1,a1);
        WasDerivedFrom wdf1 = new WasDerivedFrom(n2,n1);
        WasDerivedFrom wdf2 = new WasDerivedFrom(n1,n2);
        
        
        putVertex(p1);
        putVertex(p2);
        putVertex(p3);
        putVertex(p4);
        putVertex(n1);
        putVertex(n2);
        putVertex(a1);
        putVertex(a2);
        
        putEdge(wgb1);
        putEdge(wgb2);
        putEdge(wtb1);
        putEdge(wtb2);
        putEdge(wdf1);
        putEdge(wdf2);
        putEdge(used1);
        putEdge(used2);
    }

    @Override
    public boolean shutdown() {
        return true;
    }
}
