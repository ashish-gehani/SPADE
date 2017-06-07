/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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
package spade.edge.cdm;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;

/**
 * SimpleEdge edge based on the CDM model
 *
 */
public class SimpleEdge extends AbstractEdge{

	private static final long serialVersionUID = 1104716823846699588L;

	/**
     * Empty constructor - initializes an empty map for annotations.
     */
	public SimpleEdge(AbstractVertex childVertex, AbstractVertex parentVertex){
		setChildVertex(childVertex);
		setParentVertex(parentVertex);
		addAnnotation("type", "SimpleEdge");
	}
	
}
