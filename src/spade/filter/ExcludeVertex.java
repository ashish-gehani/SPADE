package spade.filter;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.utility.CommonFunctions;

public class ExcludeVertex extends AbstractFilter{

        private Logger logger = Logger.getLogger(this.getClass().getName());
        private String[] tokens;

        public boolean initialize(String arguments){ //arguments of the form key:value

                if(arguments == null || arguments.trim().isEmpty()){
                        logger.log(Level.WARNING,"Must specify 'annotation' arguments");
                        return false;
                }else{
                        tokens = arguments.split(":");
                        tokens[0] = tokens[0].substring(1);
                        if(tokens.length != 2){
                              logger.log(Level.WARNING,"Invalid arguments");
                              return false;
                        }else{
                              return true;
                        }
                }
        }
        @Override
	      public void putVertex(AbstractVertex incomingVertex) {
		            if(incomingVertex != null){
			                   if(!isVertexExcluded(incomingVertex,tokens)){
				                          putInNextFilter(incomingVertex);
			                   }
		            }else{
			                   logger.log(Level.WARNING, "Null vertex");
		            }
        }

        @Override
        public void putEdge(AbstractEdge incomingEdge) {
                if(incomingEdge != null && incomingEdge.getSourceVertex() != null && incomingEdge.getDestinationVertex() != null){
                        if(!isEdgeExcluded(incomingEdge,tokens)){
                                putInNextFilter(incomingEdge);
                        }
                }else{
                        logger.log(Level.WARNING, "Invalid edge: {0}, source: {1}, destination: {2}", new Object[]{
                                        incomingEdge,
                                        incomingEdge == null ? null : incomingEdge.getSourceVertex(),
                                        incomingEdge == null ? null : incomingEdge.getDestinationVertex()
                        });
                }
        }

        private boolean isVertexExcluded(AbstractVertex vertex,String[] argument){
                try{
                        Map<String,String> annotations = vertex.getAnnotations();
                        String val = annotations.get(tokens[0]);
                        return (tokens[1].equals(val));
                }catch(Exception e){
                        logger.log(Level.SEVERE,"Failed to find key: " + tokens[0],e);
                        return false;
                }
        }

        private boolean isEdgeExcluded(AbstractEdge edge,String[] argument){
                try{
                        boolean source = isVertexExcluded(edge.getSourceVertex(),argument);
                        boolean destination = isVertexExcluded(edge.getDestinationVertex(),argument);
                        Map<String,String> annotations = edge.getAnnotations();
                        String val = annotations.get(tokens[0]);
                        return (source || destination)||(tokens[1].equals(val));
                }catch(Exception e){
                        logger.log(Level.SEVERE,"Failed to find key: " + tokens[0],e);
                        return false;
                }
        }

}
