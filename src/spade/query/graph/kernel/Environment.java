/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2018 SRI International

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
package spade.query.graph.kernel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.Graph;
//import spade.query.postgresql.entities.Graph;
import spade.query.postgresql.entities.GraphMetadata;
import spade.query.graph.utility.TreeStringSerializable;

/**
 * QuickGrail compile-time environment (also used in runtime) mainly for
 * managing symbols (e.g. mapping from graph variables to their names).
 */
public class Environment extends TreeStringSerializable
{
    public final static Graph kBaseGraph = new Graph("trace_base");
    private static int id_counter = 1;

    private HashMap<String, Graph> symbols = new HashMap<>();

    private static final Logger logger = Logger.getLogger(Environment.class.getName());

    public Environment()
    {
    }

    public void clear()
    {
        symbols.clear();
    }

    public Graph allocateGraph()
    {
        String nextIdStr = String.valueOf(id_counter);
        id_counter++;
        return new Graph("trace_" + nextIdStr);
    }

    public GraphMetadata allocateGraphMetadata()
    {
        logger.log(Level.SEVERE, "Not supported");
        return null;
    }

    public String lookup(String symbol)
    {
        if(symbols.containsKey(symbol))
            return symbol;

        return null;
    }

    public void setValue(String symbol, Graph value)
    {
        if("$base".equals(symbol))
        {
            logger.log(Level.SEVERE, "Cannot reassign reserved variables.");
            throw new RuntimeException("Cannot reassign reserved variables.");
        }
        symbols.put(symbol, value);
    }

    public void eraseSymbol(String symbol)
    {
        if("$base".equals(symbol))
        {
            logger.log(Level.SEVERE, "Cannot erase reserved symbols.");
            throw new RuntimeException("Cannot erase reserved symbols.");
        }
        symbols.remove(symbol);
    }

    private boolean isGarbageTable(HashSet<String> referencedTables, String table)
    {
        logger.log(Level.SEVERE, "Not supported");
        return false;
    }

    public final Map<String, Graph> getSymbols()
    {
        return symbols;
    }

    public void gc()
    {
        logger.log(Level.SEVERE, "Not supported");
    }

    public static boolean IsBaseGraph(Graph graph)
    {
        return graph.getName().equals(kBaseGraph.getName());
    }

    @Override
    public String getLabel()
    {
        return "Environment";
    }

    @Override
    protected void getFieldStringItems(
            ArrayList<String> inline_field_names,
            ArrayList<String> inline_field_values,
            ArrayList<String> non_container_child_field_names,
            ArrayList<TreeStringSerializable> non_container_child_fields,
            ArrayList<String> container_child_field_names,
            ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields)
    {
        for(Entry<String, Graph> entry : symbols.entrySet())
        {
            inline_field_names.add(entry.getKey());
            inline_field_values.add(entry.getValue().toString());
        }
    }
}
