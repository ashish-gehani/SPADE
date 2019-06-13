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
    // fields required to fetch and return remote parts of result graph
    protected Set<Graph> combinedResultSet;
    protected Graph localResult;
    protected int depth;
    protected String direction;
    protected String function;
    protected String nonce;

    protected AbstractResolver(Graph localResult, String function, int depth, String direction, String nonce)
    {
        this.combinedResultSet = new HashSet<>();
        this.localResult = localResult;
        this.function = function;
        this.depth = depth;
        this.direction = direction;
        this.nonce = nonce;
    }

    public Set<Graph> getCombinedResultSet()
    {
        return combinedResultSet;
    }

    @Override
    public abstract void run();
}

