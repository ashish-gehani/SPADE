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
public class Agent extends AbstractVertex{

	private static final long serialVersionUID = -5661120523683066948L;

	public static final String typeValue = "Agent";

	public Agent(){
		super();
		setType(typeValue);
	}

	public Agent(final String bigHashCode){
		super(bigHashCode);
		setType(typeValue);
	}
}
