package spade.query.sql;

import spade.core.AbstractQuery;

/**
 * @author raza
 */
public abstract class SQL<R, P> extends AbstractQuery<R, P>
{
    @Override
    public abstract R execute(P parameters, Integer limit);
}
