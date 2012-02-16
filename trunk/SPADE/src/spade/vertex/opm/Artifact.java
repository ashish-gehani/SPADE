/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2011 SRI International

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
 * Artifact vertex based on the OPM model
 *
 * @author Dawood
 */
public class Artifact extends AbstractVertex {

    /**
     * Empty constructor - initializes an empty map for annotations.
     */
    public Artifact() {
        addAnnotation("type", "Artifact");
    }
}
