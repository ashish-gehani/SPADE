package spade.query.neo4j;

import spade.core.AbstractQuery;

import java.util.Map;

/**
 * Created by raza on 3/23/17.
 */
public abstract class Neo4j extends AbstractQuery
{
    @Override
    public abstract Object execute(Map parameters, Integer limit);
}
