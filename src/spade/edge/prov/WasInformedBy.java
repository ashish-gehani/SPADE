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

/**
 * WasTriggeredBy edge based on the PROV model.
 *
 * @author Dawood Tariq
 */
public class WasInformedBy extends AbstractEdge {

    /**
	 * 
	 */
	private static final long serialVersionUID = 7384759992388951575L;

	/**
     * Constructor for Activity->Activity edge
     *
     * @param informedActivity Triggered activity vertex
     * @param informingActivity Calling activity vertex
     */
    public WasInformedBy(Activity informedActivity, Activity informingActivity) {
        setChildVertex(informedActivity);
        setParentVertex(informingActivity);
        addAnnotation("type", "WasInformedBy");
    }
}
