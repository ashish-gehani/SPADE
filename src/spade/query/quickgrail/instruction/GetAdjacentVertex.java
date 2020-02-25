package spade.query.quickgrail.instruction;

import java.util.ArrayList;

import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;

public class GetAdjacentVertex extends Instruction{

	public final Graph targetGraph;
	public final Graph subjectGraph;
	public final Graph sourceGraph;
	public final GetLineage.Direction direction;
	
	public GetAdjacentVertex(Graph targetGraph, Graph subjectGraph, Graph sourceGraph,
			GetLineage.Direction direction){
		this.targetGraph = targetGraph;
		this.subjectGraph = subjectGraph;
		this.sourceGraph = sourceGraph;
		this.direction = direction;
	}
	
	@Override
	public String getLabel(){
		return this.getClass().getSimpleName();
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
		inline_field_names.add("sourceGraph");
		inline_field_values.add(sourceGraph.name);
	}

}
