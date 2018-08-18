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

import java.util.HashSet;
import java.util.Set;

/**
 * @author raza
 */
public abstract class AbstractResolver implements Runnable
{
    public static final String SOURCE_HOST = "source_host";
    public static final String SOURCE_PORT = "source_port";
    public static final String DESTINATION_HOST = "destination_host";
    public static final String DESTINATION_PORT = "destination_port";

    // fields required to fetch and return remote parts of result graph
    protected Set<Graph> finalGraph = new HashSet<>();
    protected Graph partialGraph;
    protected int depth;
    protected String direction;
    protected String function;

    protected AbstractResolver(Graph partialGraph, String function, int depth, String direction)
    {
        this.partialGraph = partialGraph;
        this.function = function;
        this.depth = depth;
        this.direction = direction;
    }

    public Set<Graph> getFinalGraph()
    {
        return finalGraph;
    }

    @Override
    public abstract void run();
}

