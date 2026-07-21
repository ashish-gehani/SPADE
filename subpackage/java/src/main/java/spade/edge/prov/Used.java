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
 * Used edge based on the PROV model.
 *
 * @author Dawood Tariq
 */
public class Used extends AbstractEdge{

	private static final long serialVersionUID = -5263470275054666497L;

	public static final String typeValue = "Used";

	/**
	 * Constructor for Activity->Entity edge
	 *
	 * @param activity Activity
	 * @param entity   Entity
	 */
	public Used(Activity activity, Entity entity){
		super();
		setChildVertex(activity);
		setParentVertex(entity);
		setType(typeValue);
	}

	public Used(final String bigHashCode, Activity activity, Entity entity){
		super(bigHashCode);
		setChildVertex(activity);
		setParentVertex(entity);
		setType(typeValue);
	}
}
