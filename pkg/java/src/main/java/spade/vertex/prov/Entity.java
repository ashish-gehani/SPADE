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
public class Entity extends AbstractVertex{

	private static final long serialVersionUID = -6124174492443466225L;

	public static final String typeValue = "Entity";

	public Entity(){
		super();
		setType(typeValue);
	}

	public Entity(final String bigHashCode){
		super(bigHashCode);
		setType(typeValue);
	}
}
