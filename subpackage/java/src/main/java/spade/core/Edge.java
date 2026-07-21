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
package spade.core;

/**
 * A general-purpose, semantic-agnostic implementation of the Edge class.
 *
 * @author Dawood Tariq
 */
public class Edge extends AbstractEdge{

	private static final long serialVersionUID = -2356914905546316322L;

	public static final String typeValue = "Edge";

	/**
	 * Constructor taking only the source and destination vertices.
	 *
	 * @param childVertex  Source vertex
	 * @param parentVertex Destination vertex
	 */
	public Edge(AbstractVertex childVertex, AbstractVertex parentVertex){
		super();
		setChildVertex(childVertex);
		setParentVertex(parentVertex);
		setType(typeValue);
	}

	public Edge(String bigHashCode, AbstractVertex childVertex, AbstractVertex parentVertex){
		super(bigHashCode);
		setChildVertex(childVertex);
		setParentVertex(parentVertex);
		setType(typeValue);
	}
}
