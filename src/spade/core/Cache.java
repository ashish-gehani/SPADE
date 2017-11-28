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

/**
 * This class encapsulates the caching policy and implementation
 * for graph vertices and edges.
 *
 * @author Raza Ahmad
 */
public class Cache<T> {

    /**
     * This function checks for the presence of given object in the underlying cache(s).
     * @param object object to check the presence of
     * @return returns true if the object is found in cache
     */
    public static<T> boolean isPresent(T object)
    {
        return false;
    }

    /**
     * This function adds an item to the underlying cache(s).
     * @param object object to add to the cache(s).
     * @return returns true if the object has been successfully added to the cache
     */
    public static<T> boolean addItem(T object)
    {
        return false;
    }
}
