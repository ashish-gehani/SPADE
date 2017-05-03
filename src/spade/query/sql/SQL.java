package spade.query.sql;

import spade.core.AbstractQuery;

import java.util.List;
import java.util.Map;

/**
 * @author raza
 */
public abstract class SQL<R, P> extends AbstractQuery<R, P>
{
    @Override
    public abstract R execute(P parameters, Integer limit);
}
