package spade.query.sql;

import spade.core.AbstractQuery;

import java.util.List;
import java.util.Map;

/**
 * @author raza
 */
public abstract class SQL<R, P> extends AbstractQuery<R, P>
{
    // indices of variables in parameter map of GetVertex and GetEdge
    protected static final int ARITHMETIC_OPERATOR = 0;
    protected static final int COL_VALUE = 1;
    protected static final int BOOLEAN_OPERATOR = 2;

    @Override
    public abstract R execute(P parameters, Integer limit);
}
