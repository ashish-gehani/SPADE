/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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
package spade.storage;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.deri.iris.Configuration;
import org.deri.iris.EvaluationException;
import org.deri.iris.KnowledgeBaseFactory;
import org.deri.iris.api.IKnowledgeBase;
import org.deri.iris.api.basics.IPredicate;
import org.deri.iris.api.basics.IQuery;
import org.deri.iris.api.basics.IRule;
import org.deri.iris.api.basics.ITuple;
import org.deri.iris.api.terms.IVariable;
import org.deri.iris.compiler.Parser;
import org.deri.iris.compiler.ParserException;
import org.deri.iris.storage.IRelation;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Graph;

/**
 *
 * @author Dawood Tariq
 */
public class Datalog extends AbstractStorage {

    StringBuilder datalogProgram = new StringBuilder();
    Map<Long, AbstractVertex> vertexMap = new HashMap<>();
    Map<AbstractVertex, Long> vertexMapReversed = new HashMap<>();
    static final Logger logger = Logger.getLogger(Datalog.class.getName());

    @Override
    public boolean initialize(String arguments) {
        return true;
    }

    @Override
    public boolean shutdown() {
        return true;
    }

    @Override
    public boolean storeVertex(AbstractVertex incomingVertex) {
        vertexMap.put(getVertexCount(), incomingVertex);
        vertexMapReversed.put(incomingVertex, getVertexCount());
        return true;
    }

    @Override
    public ResultSet executeQuery(String query)
    {
        return null;
    }

    @Override
    public boolean storeEdge(AbstractEdge incomingEdge) {
        long childVertexId = vertexMapReversed.get(incomingEdge.getChildVertex());
        long parentVertexId = vertexMapReversed.get(incomingEdge.getParentVertex());
        datalogProgram.append("parent('").append(childVertexId).append("', '").append(parentVertexId).append("')\r\n");
        return true;
    }

    public Graph getLineage(String vertexExpression, int depth, String direction, String terminatingExpression) {
        Graph result = new Graph();
        String program = datalogProgram.toString();
        program += "ancestor(?x, ?y) :- parent(?x, ?y).\r\n";
        program += "ancestor(?x, ?y) :- parent(?x, ?z), ancestor(?z, ?y).\r\n";
        program += "?-ancestor('" + vertexExpression + "', ?x).";

        try {
            Configuration configuration = KnowledgeBaseFactory.getDefaultConfiguration();
            Parser parser = new Parser();
            parser.parse(program);

            Map<IPredicate, IRelation> facts = parser.getFacts();
            List<IRule> rules = parser.getRules();
            IKnowledgeBase knowledgeBase = KnowledgeBaseFactory.createKnowledgeBase(facts, rules, configuration);

            List<IVariable> variableBindings = new ArrayList<>();
            IQuery query = parser.getQueries().get(0);
            IRelation results = knowledgeBase.execute(query, variableBindings);

            for (int t = 0; t < results.size(); ++t) {
                ITuple tuple = results.get(t);
                long id = Integer.parseInt(tuple.toString().substring(2, tuple.toString().length() - 2));
                AbstractVertex v = vertexMap.get(id);
                result.putVertex(v);
            }

        } catch (ParserException | EvaluationException | NumberFormatException exception) {
            logger.log(Level.SEVERE, null, exception);
            return null;
        }

        return result;
    }


}
