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
public class Entity extends AbstractVertex {

    /**
	 * 
	 */
	private static final long serialVersionUID = 4737740472976211063L;

	public Entity() {
        addAnnotation("type", "Entity");
    }
}
