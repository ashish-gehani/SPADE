package spade.query.common;

import spade.core.AbstractQuery;
import spade.core.Graph;

import java.util.List;
import java.util.Map;

/**
 * @author raza
 */
public class GetPaths extends AbstractQuery<Graph, Map<String, List<String>>>
{
    @Override
    public Graph execute(Map<String, List<String>> parameters, Integer limit)
    {
        return null;
    }
}
