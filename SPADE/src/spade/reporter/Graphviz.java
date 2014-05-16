/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2014 SRI International

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

import java.util.logging.Level;
import java.util.logging.Logger;
import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.core.Graph;

/**
 *
 * @author Dawood Tariq
 */
public class Graphviz extends AbstractReporter {

    static final Logger logger = Logger.getLogger(Graphviz.class.getName());

    @Override
    public boolean launch(String arguments) {
        if (arguments == null) {
            return false;
        }
        try {
            Graph graph = Graph.importGraph(arguments);
            for (AbstractVertex v : graph.vertexSet()) {
                putVertex(v);
            }
            for (AbstractEdge e : graph.edgeSet()) {
                putEdge(e);
            }
            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }

    @Override
    public boolean shutdown() {
        return true;
    }
}
