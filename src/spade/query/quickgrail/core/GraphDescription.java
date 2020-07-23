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
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import spade.query.quickgrail.instruction.DescribeGraph.DescriptionType;
import spade.query.quickgrail.instruction.DescribeGraph.ElementType;
import spade.query.quickgrail.types.StringType;
import spade.query.quickgrail.utility.ResultTable;
import spade.query.quickgrail.utility.Schema;

public class GraphDescription implements Serializable{

	private static final long serialVersionUID = -8661388010852548803L;
	
	public final ElementType elementType;
	public final String annotationName;
	public final DescriptionType descriptionType;
	
	public final boolean all;
	
	private final TreeSet<String> annotations = new TreeSet<String>();
	
	private final TreeMap<String, Long> valueToCount = new TreeMap<String, Long>();
	
	private String min = null, max = null;
	
	public GraphDescription(final ElementType elementType,
			final String annotationName, final DescriptionType descriptionType){
		this.elementType = elementType;
		this.annotationName = annotationName;
		this.descriptionType = descriptionType;
		this.all = false;
	}
	
	public GraphDescription(final ElementType elementType){
		this.elementType = elementType;
		this.annotationName = null;
		this.descriptionType = null;
		this.all = true;
	}
	
	public final void addAnnotation(final String annotation){
		if(annotation != null){
			this.annotations.add(annotation);
		}
	}
	
	public final void addAnnotations(final Collection<String> annotations){
		if(annotations != null){
			for(final String annotation : annotations){
				addAnnotation(annotation);
			}
		}
	}
	
	public final void incrementValueToCount(final String value){
		if(value != null){
			Long count = valueToCount.get(value);
			if(count == null){
				count = 0L;
			}
			count++;
			valueToCount.put(value, count);
		}
	}
	
	public final void putValueToCount(final String value, final Long count){
		if(value != null){
			valueToCount.put(value, count);
		}
	}
	
	public final void setMinMax(final String min, final String max){
		this.min = min;
		this.max = max;
	}
	
	public String toString(){
		final String elementTypeName;
		if(elementType == null){
			throw new RuntimeException("NULL element type");
		}
		switch(elementType){
			case VERTEX:
			case EDGE:
				elementTypeName = elementType.name(); break;
			default: throw new RuntimeException("Unhandled element type: " + elementType);
		}
		if(all){
			final ResultTable table = new ResultTable();
			final Iterator<String> iterator = annotations.iterator();
			while(iterator.hasNext()){
				String annotation = iterator.next();
				ResultTable.Row row = new ResultTable.Row();
				row.add(annotation);
				table.addRow(row);
			}
			Schema schema = new Schema();
			schema.addColumn(elementTypeName + " annotations", StringType.GetInstance());
			table.setSchema(schema);
			return table.toString();
		}else{
			if(annotationName == null){
				throw new RuntimeException("NULL annotation name");
			}
			switch(descriptionType){
				case COUNT:{
					final ResultTable table = new ResultTable();
					for(final Map.Entry<String, Long> entry : valueToCount.entrySet()){
						ResultTable.Row row = new ResultTable.Row();
						row.add(entry.getKey());
						row.add(entry.getValue());
						table.addRow(row);
					}
					Schema schema = new Schema();
					schema.addColumn(getFormattedHeading(elementTypeName, annotationName), StringType.GetInstance());
					schema.addColumn("Count", StringType.GetInstance());
					table.setSchema(schema);
					return table.toString();
				}
				case MINMAX:{
					final ResultTable table = new ResultTable();
					
					ResultTable.Row row = new ResultTable.Row();
					row.add(annotationName);
					row.add(min);
					row.add(max);
					table.addRow(row);
					
					Schema schema = new Schema();
					schema.addColumn(elementTypeName + " annotation", StringType.GetInstance());
					schema.addColumn("Min", StringType.GetInstance());
					schema.addColumn("Max", StringType.GetInstance());
					table.setSchema(schema);
					return table.toString();
				}
				default: throw new RuntimeException("Unhandled description type: " + descriptionType);
			}
		}
	}
	
	private final String getFormattedHeading(final String elementTypeName, final String annotationName){
		return elementTypeName + "[" + annotationName + "]";
	}
}
