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
package spade.vertex.opm;

import spade.core.AbstractVertex;

/**
 * Process vertex based on the OPM model
 *
 * @author Dawood Tariq
 */
public class Process extends AbstractVertex{

	private static final long serialVersionUID = 3975681077544176691L;

	public static final String typeValue = "Process";

	/**
	 * Empty constructor - initializes an empty map for annotations.
	 */
	public Process(){
		super();
		setType(typeValue);
	}

	public Process(final String bigHashCode){
		super(bigHashCode);
		setType(typeValue);
	}
}
