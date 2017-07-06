package spade.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author raza
 */
public abstract class AbstractQuery<R, P>
{
    // indices of variables in parameter map of GetVertex and GetEdge
    protected static final int ARITHMETIC_OPERATOR = 0;
    protected static final int COL_VALUE = 1;
    protected static final int BOOLEAN_OPERATOR = 2;

    protected static final Integer DEFAULT_LIMIT = 100;

    protected static AbstractStorage currentStorage;
    protected static final String DIRECTION_ANCESTORS = Settings.getProperty("direction_ancestors");
    protected static final String DIRECTION_DESCENDANTS = Settings.getProperty("direction_descendants");

    public interface OPERATORS
    {
        String EQUALS = "=";
        String NOT_EQUALS = "!=";
        String LESS_THAN = "<";
        String LESS_THAN_EQUALS = "<=";
        String GREATER_THAN = ">";
        String GREATER_THAN_EQUALS = ">=";
    }

    private boolean hasRegistered = false;

    public abstract R execute(P parameters, Integer limit);

    public static void setCurrentStorage(AbstractStorage storage)
    {
        currentStorage = storage;
    }

    public void register()
    {
        try
        {
            Class[] paramTypes = new Class[]{java.util.Map.class, java.lang.Integer.class};
            if(!hasRegistered)
            {
                AbstractAnalyzer.registerFunction(this.getClass().getSimpleName(),
                        this.getClass().getName(),
                        this.getClass().getDeclaredMethod("execute", paramTypes).getReturnType().getName());
                hasRegistered = true;
            }
        }
        catch(NoSuchMethodException ex)
        {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "No execute method found", ex);
        }
    }
}
