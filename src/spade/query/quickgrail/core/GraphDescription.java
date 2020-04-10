/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

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
package spade.query.quickgrail.core;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class GraphDescription implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = -6317483938918491695L; // TODO redo
	
	private final Set<String> vertexAnnotations = new HashSet<String>();
	private final Set<String> edgeAnnotations = new HashSet<String>();
	
	// TODO toString
	
}
