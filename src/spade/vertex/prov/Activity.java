/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package spade.vertex.prov;

import spade.core.AbstractVertex;

/**
 *
 * @author dawood
 */
public class Activity extends AbstractVertex {

    /**
	 * 
	 */
	private static final long serialVersionUID = 5932263768224584090L;

	public Activity() {
        addAnnotation("type", "Activity");
    }
}
