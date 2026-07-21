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
public class Activity extends AbstractVertex{

	private static final long serialVersionUID = 1918292905350967767L;

	public static final String typeValue = "Activity";

	public Activity(){
		super();
		setType(typeValue);
	}

	public Activity(final String bigHashCode){
		super(bigHashCode);
		setType(typeValue);
	}
}
