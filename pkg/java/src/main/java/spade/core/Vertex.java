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
 * A general-purpose, semantic-agnostic implementation of the Vertex class.
 *
 * @author Dawood Tariq
 */
public class Vertex extends AbstractVertex{

	private static final long serialVersionUID = -4361039611805843903L;

	public static final String typeValue = "Vertex";

	/**
	 * An empty constructor - an empty map is initialized for the annotations.
	 */
	public Vertex(){
		super();
		setType(typeValue);
	}

	public Vertex(String bigHashCode){
		super(bigHashCode);
		setType(typeValue);
	}
}
