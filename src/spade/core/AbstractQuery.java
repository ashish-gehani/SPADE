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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static spade.core.AbstractAnalyzer.BOOLEAN_OPERATORS;
import static spade.core.AbstractAnalyzer.COMPARISON_OPERATORS;

/**
 * @author raza
 */
public abstract class AbstractQuery<R>
{
    // indices of variables in parameter map of GetVertex and GetEdge
    protected static final int COMPARISON_OPERATOR = 0;
    public static final int COL_VALUE = 1;
    protected static final int BOOLEAN_OPERATOR = 2;

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

    public abstract R execute(String argument_string);

    public abstract R execute(Map<String, List<String>> parameters, Integer limit);

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

    protected Map<String, List<String>> parseConstraints(String constraints)
    {
        Map<String, List<String>> parameters = new HashMap<>();
        // get the argument expression(s), split by the boolean operators
        // The format for one argument is:
        // <key> COMPARISON_OPERATOR <value> [BOOLEAN_OPERATOR]
        Pattern constraints_pattern = Pattern.compile("((?i)(?<=(\\b" + BOOLEAN_OPERATORS + "\\b))|" +
                "((?=(\\b" + BOOLEAN_OPERATORS + "\\b))))");
        String[] expressions = constraints_pattern.split(constraints);

        // extract the key value pairs
        int i = 0;
        while(i < expressions.length)
        {
            String expression = expressions[i];
            Pattern expression_pattern = Pattern.compile("((?<=(" + COMPARISON_OPERATORS + "))|" +
                    "(?=(" + COMPARISON_OPERATORS + ")))");
            String[] operands = expression_pattern.split(expression);
            String key = operands[0].trim();
            String operator = operands[1].trim();
            String value = operands[2].trim();

            List<String> values = new ArrayList<>();
            values.add(operator);
            values.add(value);
            i++;
            // if boolean operator is present
            if(i < expressions.length &&
                    BOOLEAN_OPERATORS.toLowerCase().contains(expressions[i].toLowerCase()))
            {
                String bool_operator = expressions[i].trim();
                values.add(bool_operator);
                i++;
            }
            else
            {
                values.add(null);
            }

            parameters.put(key, values);
        }

        return parameters;
    }
}
