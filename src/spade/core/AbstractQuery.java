package spade.core;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author raza
 */
public abstract class AbstractQuery<R, P>
{
    protected AbstractStorage currentStorage;

    protected interface OPERATORS
    {
        String EQUALS = "=";
        String NOT_EQUALS = "!=";
        String LESS_THAN = "<";
        String LESS_THAN_EQUALS = "<=";
        String GREATER_THAN = ">";
        String GREATER_THAN_EQUALS = ">=";
    }

    public abstract R execute(P parameters, Integer limit);

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
