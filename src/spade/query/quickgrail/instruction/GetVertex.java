/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

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
package spade.query.quickgrail.instruction;

import java.util.ArrayList;

import spade.query.quickgrail.core.QuickGrailQueryResolver.PredicateOperator;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;

public class GetVertex extends Instruction{

	public final Graph targetGraph, subjectGraph;
	public final String annotationKey;
	public final PredicateOperator operator;
	public final String annotationValue;
	
	private boolean hasArguments = false;
	
	public GetVertex(Graph targetGraph, Graph subjectGraph, 
			String annotationKey, PredicateOperator operator, String annotationValue){
		this.targetGraph = targetGraph;
		this.subjectGraph = subjectGraph;
		this.annotationKey = annotationKey;
		this.operator = operator;
		this.annotationValue = annotationValue;
		this.hasArguments = true;
	}
	
	public GetVertex(Graph targetGraph, Graph subjectGraph){
		this(targetGraph, subjectGraph, null, null, null);
		this.hasArguments = false;
	}
	
	public boolean hasArguments(){
		return hasArguments;
	}
	
	@Override
	public String getLabel(){
		return "GetVertex";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("targetGraph");
		inline_field_values.add(targetGraph.name);
		inline_field_names.add("subjectGraph");
		inline_field_values.add(subjectGraph.name);
		inline_field_names.add("annotationKey");
		inline_field_values.add(String.valueOf(annotationKey));
		inline_field_names.add("operator");
		inline_field_values.add(String.valueOf(operator));
		inline_field_names.add("annotationValue");
		inline_field_values.add(String.valueOf(annotationValue));
	}

}
