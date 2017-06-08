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
package spade.edge.prov;

import spade.core.AbstractEdge;
import spade.vertex.prov.Entity;

/**
 * WasDerivedFrom edge based on the PROV model.
 *
 * @author Dawood Tariq
 */
public class WasDerivedFrom extends AbstractEdge {

    /**
	 * 
	 */
	private static final long serialVersionUID = -7510649079940497925L;

	/**
     * Constructor for Entity->Entity edge
     *
     * @param sourceEntity Source entity vertex
     * @param destinationEntity Destination entity vertex
     */
    public WasDerivedFrom(Entity sourceEntity, Entity destinationEntity) {
        setChildVertex(sourceEntity);
        setParentVertex(destinationEntity);
        addAnnotation("type", "WasDerivedFrom");
    }
}
