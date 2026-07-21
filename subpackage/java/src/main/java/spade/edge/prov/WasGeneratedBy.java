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
import spade.vertex.prov.Activity;
import spade.vertex.prov.Entity;

/**
 * WasGeneratedBy edge based on the PROV model.
 *
 * @author Dawood Tariq
 */
public class WasGeneratedBy extends AbstractEdge{

	private static final long serialVersionUID = 9063692509254013278L;

	public static final String typeValue = "WasGeneratedBy";

	/**
	 * Constructor for Entity->Activity edge
	 *
	 * @param entity   Entity
	 * @param activity Activity
	 */
	public WasGeneratedBy(Entity entity, Activity activity){
		super();
		setChildVertex(entity);
		setParentVertex(activity);
		setType(typeValue);
	}

	public WasGeneratedBy(final String bigHashCode, Entity entity, Activity activity){
		super(bigHashCode);
		setChildVertex(entity);
		setParentVertex(activity);
		setType(typeValue);
	}
}
