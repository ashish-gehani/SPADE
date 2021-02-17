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
package spade.filter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.core.Vertex;

public class Fusion extends AbstractFilter {

    private LinkedList<Object> leftList;
    private LinkedList<Object> rightList;
    private Map<AbstractVertex, AbstractVertex> fusedVertices;
    private Map<RuleIdentifier, RuleIdentifier> rules;
    private final String configFile = Settings.getDefaultConfigFilePath(this.getClass());
    private final String SOURCE_REPORTER = Settings.getSourceReporter();
    private int MAX_LIST_LENGTH = 5;
    private String leftReporter;
    private String rightReporter;
    private String FUSED_SOURCE_REPORTER;

    public Fusion() {
        // The left and right lists are used to store provenance elements from the
        // two reporters respectively. Elements are added and removed using the queue
        // interface but the lists are initialized as LinkedLists to allow for
        // traversal and checking for matching elements
        leftList = new LinkedList<>();
        rightList = new LinkedList<>();

        // The fusedVertices is a map containing all the previously-fused vertices.
        // This is checked when new vertices and edges are received by this filter
        // for quick replacement
        fusedVertices = new HashMap<>();

        // The rules map is used to store the rules. This is used in the compare
        // function
        rules = new HashMap<>();

        // Read and process the configuration file. Currently, the file syntax is:
        // -- BEGIN FILE --
        // <1st reporter>
        // <2nd reporter>
        // <1st reporter>.<annotation>=<2nd reporter>.<annotation>
        // -- EOF --
        try {
            BufferedReader configReader = new BufferedReader(new FileReader(configFile));
            leftReporter = configReader.readLine();
            rightReporter = configReader.readLine();
            FUSED_SOURCE_REPORTER = leftReporter + " + " + rightReporter;
            String ruleLine = configReader.readLine();
            String leftRuleString = ruleLine.split("=")[0].trim();
            RuleIdentifier leftRule = new RuleIdentifier(leftRuleString);
            String rightRuleString = ruleLine.split("=")[1].trim();
            RuleIdentifier rightRule = new RuleIdentifier(rightRuleString);
            rules.put(leftRule, rightRule);
            rules.put(rightRule, leftRule);
        } catch (Exception exception) {
            Logger.getLogger(Fusion.class.getName()).log(Level.SEVERE, null, exception);
        }
    }

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        // The 'compare' boolean flag is used to determine whether the lists needed
        // to be traversed and checked when the element is added. This is needed
        // because comparison is unnecessary in the following cases:
        // 1) When an already-fused vertex is added to the list
        // 2) When an edge is added to the list
        boolean compare = true;

        // If this vertex has already been fused before, replace it.
        if (fusedVertices.containsKey(incomingVertex)) {
            incomingVertex = fusedVertices.get(incomingVertex);
            compare = false;
        }

        // Determine the source reporter of the incoming vertex so that it is added
        // to the appropriate list and the checkLists() function is called on the
        // correct lists
        String incomingSource = incomingVertex.getAnnotation(SOURCE_REPORTER);
        if (incomingSource.equalsIgnoreCase(leftReporter)) {
            leftList.add(incomingVertex);
            checkLists(leftList, rightList, compare);
        } else if (incomingSource.equalsIgnoreCase(rightReporter)) {
            rightList.add(incomingVertex);
            checkLists(rightList, leftList, compare);
        } else {
            // If the incoming vertex is from any other reporter, simply forward it
            putInNextFilter(incomingVertex);
        }
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        // Determine if the source or destination vertices of this edge have been
        // fused before. If yes, then replace them with the fused vertices
        if (fusedVertices.containsKey(incomingEdge.getChildVertex())) {
            incomingEdge.setChildVertex(fusedVertices.get(incomingEdge.getChildVertex()));
        }
        if (fusedVertices.containsKey(incomingEdge.getParentVertex())) {
            incomingEdge.setParentVertex(fusedVertices.get(incomingEdge.getParentVertex()));
        }

        // Determine the source reporter of the incoming vertex so that it is added
        // to the appropriate list. The checkLists() function is called without
        // comparison because we do not fuse edges
        String incomingSource = incomingEdge.getAnnotation(SOURCE_REPORTER);
        if (incomingSource.equalsIgnoreCase(leftReporter)) {
            leftList.add(incomingEdge);
            checkLists(leftList, rightList, false);
        } else if (incomingSource.equalsIgnoreCase(rightReporter)) {
            rightList.add(incomingEdge);
            checkLists(rightList, leftList, false);
        } else {
            // If the incoming edge is from any other reporter, forward it
            putInNextFilter(incomingEdge);
        }
    }

    private void checkLists(LinkedList sourceList, LinkedList destinationList, boolean compare) {
        // Comparison and fusion is done if the compare flag is set
        if (compare) {
            // Get the most recently added element to the source list. This is
            // compared to all the elements in the other list
            Object newElement = sourceList.peekLast();
            if (newElement instanceof AbstractVertex) {
                AbstractVertex newVertex = (AbstractVertex) newElement;
                for (int i = 0; i < destinationList.size(); i++) {
                    Object otherElement = destinationList.get(i);
                    if (otherElement instanceof AbstractVertex) {
                        AbstractVertex otherVertex = (AbstractVertex) otherElement;
                        // This condition is used to check for elements in the list that
                        // have already been fused in which case comparison and fusion
                        // is unnecessary
                        if (!otherVertex.getAnnotation(SOURCE_REPORTER).equalsIgnoreCase(FUSED_SOURCE_REPORTER)
                                && compare(newVertex, otherVertex)) {
                            fuseAndReplace(newVertex, otherVertex);
                        }
                    }
                }
            }
        }
        // Check if the list size is exceeded. If yes, remove the element from
        // the head of the list and forward it to the next filter
        if (sourceList.size() > MAX_LIST_LENGTH) {
            Object removedElement = sourceList.poll();
            if (removedElement instanceof AbstractVertex) {
                putInNextFilter((AbstractVertex) removedElement);
            } else if (removedElement instanceof AbstractEdge) {
                putInNextFilter((AbstractEdge) removedElement);
            }
        }
    }

    private boolean compare(AbstractVertex firstVertex, AbstractVertex secondVertex) {
        // Use the rules map to determine if the two vertices match. First, the
        // source reporters are checked and then the annotations
        for (RuleIdentifier currentRule : rules.keySet()) {
            RuleIdentifier otherRule = rules.get(currentRule);
            if (firstVertex.getAnnotation(SOURCE_REPORTER).equalsIgnoreCase(currentRule.reporter)
                    && secondVertex.getAnnotation(SOURCE_REPORTER).equalsIgnoreCase(otherRule.reporter)) {
                String firstValue = firstVertex.getAnnotation(currentRule.annotation);
                String secondValue = secondVertex.getAnnotation(currentRule.annotation);
                if (firstValue != null && secondValue != null && firstValue.equalsIgnoreCase(secondValue)) {
                    return true;
                }
            }
        }
        // If no match is found in the rules map for these two vertices,
        // return false
        return false;
    }

    private void fuseAndReplace(AbstractVertex firstVertex, AbstractVertex secondVertex) {
        // Create a new fused vertex and add all annotations of the first and second
        // vertices. The 'source reporter' annotation is changed to reflect that
        // this vertex is now fused
        AbstractVertex fusedVertex = new Vertex();
        fusedVertex.addAnnotations(firstVertex.getCopyOfAnnotations());
        fusedVertex.addAnnotations(secondVertex.getCopyOfAnnotations());
        fusedVertex.addAnnotation(SOURCE_REPORTER, FUSED_SOURCE_REPORTER);

        // Create references to the two lists which are assigned by evaluating
        // the source reporters for the two vertices. These references are used
        // when replacing vertices in the lists with the fused vertex
        LinkedList firstList = null;
        LinkedList secondList = null;
        String firstSource = firstVertex.getAnnotation(SOURCE_REPORTER);
        if (firstSource.equalsIgnoreCase(leftReporter)) {
            firstList = leftList;
            secondList = rightList;
        } else if (firstSource.equalsIgnoreCase(rightReporter)) {
            firstList = rightList;
            secondList = leftList;
        }

        // Replace all occurrences of a vertex in it's list by the fused vertex.
        // Also, check if the source or destination vertices of any edges in this
        // list point to the vertex we are replacing. If yes, replace those
        // references by the fused vertex as well
        for (int i = 0; i < firstList.size(); i++) {
            if (firstVertex.equals(firstList.get(i))) {
                firstList.set(i, fusedVertex);
            } else if (firstList.get(i) instanceof AbstractEdge) {
                AbstractEdge tempEdge = (AbstractEdge) firstList.get(i);
                if (tempEdge.getChildVertex().equals(firstVertex)) {
                    tempEdge.setChildVertex(fusedVertex);
                } else if (tempEdge.getParentVertex().equals(firstVertex)) {
                    tempEdge.setParentVertex(fusedVertex);
                }
            }
        }
        // Perform the same function for the other vertex and the other list
        for (int i = 0; i < secondList.size(); i++) {
            if (secondVertex.equals(secondList.get(i))) {
                secondList.set(i, fusedVertex);
            } else if (secondList.get(i) instanceof AbstractEdge) {
                AbstractEdge tempEdge = (AbstractEdge) secondList.get(i);
                if (tempEdge.getChildVertex().equals(secondVertex)) {
                    tempEdge.setChildVertex(fusedVertex);
                } else if (tempEdge.getParentVertex().equals(secondVertex)) {
                    tempEdge.setParentVertex(fusedVertex);
                }
            }
        }

        // Finally, add the fused vertex to the map
        fusedVertices.put(firstVertex, fusedVertex);
        fusedVertices.put(secondVertex, fusedVertex);
    }

    @Override
    public boolean shutdown() {
        while (MAX_LIST_LENGTH > 0) {
            checkLists(leftList, rightList, true);
            checkLists(rightList, leftList, true);
            MAX_LIST_LENGTH--;
        }
        return true;
    }
}

class RuleIdentifier {

    public String reporter;
    public String annotation;

    public RuleIdentifier(String ruleString) {
        this.reporter = ruleString.split("\\.")[0];
        this.annotation = ruleString.split("\\.")[1];
    }

    public RuleIdentifier(String reporter, String annotation) {
        this.reporter = reporter;
        this.annotation = annotation;
    }
}
