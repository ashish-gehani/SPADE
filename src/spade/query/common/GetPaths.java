package spade.query.common;

import spade.core.AbstractQuery;
import spade.core.Graph;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        //TODO: order of hashes need to be changed here
        GetLineage getLineage = new GetLineage();
        parameters.put("direction", Collections.singletonList(DIRECTION_ANCESTORS));
        Graph ancestorLineage = getLineage.execute(parameters, limit);
        parameters.put("direction", Collections.singletonList(DIRECTION_DESCENDANTS));
        Graph descendantLineage = getLineage.execute(parameters, limit);

        return Graph.union(ancestorLineage, descendantLineage);
    }
}
