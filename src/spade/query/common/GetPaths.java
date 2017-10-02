package spade.query.common;

import spade.core.AbstractQuery;
import spade.core.Graph;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static spade.core.AbstractStorage.DIRECTION_ANCESTORS;
import static spade.core.AbstractStorage.DIRECTION_DESCENDANTS;
import static spade.core.AbstractStorage.PRIMARY_KEY;

/**
 * @author raza
 */
public class GetPaths extends AbstractQuery<Graph, Map<String, List<String>>>
{
    public GetPaths()
    {
        register();
    }

    @Override
    public Graph execute(Map<String, List<String>> parameters, Integer limit)
    {
        // Implicit assumption that 'sourceVertexHash' and 'destinationVertexHash' keys are present
        GetLineage getLineage = new GetLineage();
        parameters.put(PRIMARY_KEY, parameters.get("sourceVertexHash"));
        parameters.put("direction", Collections.singletonList(DIRECTION_ANCESTORS));
        Graph ancestorLineage = getLineage.execute(parameters, limit);
        parameters.put("direction", Collections.singletonList(DIRECTION_DESCENDANTS));
        parameters.put(PRIMARY_KEY, parameters.get("destinationVertexHash"));
        Graph descendantLineage = getLineage.execute(parameters, limit);

        return Graph.union(ancestorLineage, descendantLineage);
    }
}
