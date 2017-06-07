package spade.core;

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

    public abstract R execute(P parameters, Integer limit);

    public static void setCurrentStorage(AbstractStorage storage)
    {
        currentStorage = storage;
    }

    public void register()
    {
        try
        {
            AbstractAnalyzer.registerFunction(this.getClass().getSimpleName(),
                    this.getClass().getName(),
                    this.getClass().getMethod("execute").getReturnType().toString());
        }
        catch(NoSuchMethodException ex)
        {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "No execute method found", ex);
        }
    }
}
