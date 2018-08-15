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
    protected static final int COMPARISON_OPERATOR = 0;
    public static final int COL_VALUE = 1;
    protected static final int BOOLEAN_OPERATOR = 2;

    protected static final Integer DEFAULT_MIN_LIMIT = 1;
    protected static final Integer DEFAULT_MAX_LIMIT = 100;

    protected static AbstractStorage currentStorage;

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

    public static AbstractStorage getCurrentStorage()
    {
        return currentStorage;
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
