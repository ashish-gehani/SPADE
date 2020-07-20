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
package spade.query.quickgrail.core;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;

import spade.query.quickgrail.types.StringType;
import spade.query.quickgrail.utility.ResultTable;
import spade.query.quickgrail.utility.Schema;

public class GraphDescription implements Serializable{

	private static final long serialVersionUID = -8661388010852548803L;
	
	private final TreeSet<String> vertexAnnotations = new TreeSet<String>();
	private final TreeSet<String> edgeAnnotations = new TreeSet<String>();
	
	private final void addAnnotation(final TreeSet<String> annotationsSet, final String annotation){
		if(annotationsSet != null && annotation != null){
			annotationsSet.add(annotation);
		}
	}
	
	public final void addVertexAnnotation(final String annotation){
		addAnnotation(this.vertexAnnotations, annotation);
	}
	
	public final void addVertexAnnotations(final Collection<String> vertexAnnotations){
		if(vertexAnnotations != null){
			for(final String vertexAnnotation : vertexAnnotations){
				addVertexAnnotation(vertexAnnotation);
			}
		}
	}
	
	public final void addEdgeAnnotation(final String annotation){
		addAnnotation(this.edgeAnnotations, annotation);
	}
	
	public final void addEdgeAnnotations(final Collection<String> edgeAnnotations){
		if(edgeAnnotations != null){
			for(final String edgeAnnotation : edgeAnnotations){
				addEdgeAnnotation(edgeAnnotation);
			}
		}
	}
	
	public final TreeSet<String> getVertexAnnotations(){
		return new TreeSet<String>(this.vertexAnnotations);
	}
	
	public final TreeSet<String> getEdgeAnnotations(){
		return new TreeSet<String>(this.edgeAnnotations);
	}
	
	public String toString(){
		final ResultTable table = new ResultTable();
		final Iterator<String> vertexIterator = vertexAnnotations.iterator();
		final Iterator<String> edgeIterator = edgeAnnotations.iterator();
		while(vertexIterator.hasNext() || edgeIterator.hasNext()){
			String vertexAnnotation = vertexIterator.hasNext() ? vertexIterator.next() : "";
			String edgeAnnotation = edgeIterator.hasNext() ? edgeIterator.next() : "";
			ResultTable.Row row = new ResultTable.Row();
			row.add(vertexAnnotation);
			row.add(edgeAnnotation);
			table.addRow(row);
		}
		
		Schema schema = new Schema();
		schema.addColumn("Vertex Annotations", StringType.GetInstance());
		schema.addColumn("Edge Annotations", StringType.GetInstance());
		table.setSchema(schema);
		return table.toString();
	}
}
