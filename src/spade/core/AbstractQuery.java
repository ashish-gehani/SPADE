/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2017 SRI International
 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.
 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
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
