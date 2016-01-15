/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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

package spade.core;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DigQueryParams{

	private String storage;
	private String operation;
	private String expression;
	private String vertexId;
	private AbstractVertex vertex;
	private String srcVertexId;
	private AbstractVertex srcVertex;
	private String dstVertexId;
	private AbstractVertex dstVertex;
	private Integer maxLength;
	private Integer depth;
	private String direction;
	private String terminatingExpression;

	private DigQueryParams(){}
	
	public static DigQueryParams parseQuery(String query){
		try{
			DigQueryParams digQueryParams = new DigQueryParams();
			String tokens[] = query.split("\\s+");
			digQueryParams.storage = tokens[1];
			digQueryParams.operation = tokens[2];
			if(digQueryParams.operation.equals("lineage")){
				digQueryParams.vertexId = tokens[3];
				digQueryParams.vertex = getVertexForId(digQueryParams.getStorage(), digQueryParams.vertexId);
				digQueryParams.depth = Integer.parseInt(tokens[4]);
				digQueryParams.direction = tokens[5];
				digQueryParams.terminatingExpression = tokens[6];
			}else if(digQueryParams.operation.equals("paths")){
				digQueryParams.srcVertexId = tokens[3];
				digQueryParams.srcVertex = getVertexForId(digQueryParams.getStorage(), digQueryParams.srcVertexId);
				digQueryParams.dstVertexId = tokens[4];
				digQueryParams.dstVertex = getVertexForId(digQueryParams.getStorage(), digQueryParams.dstVertexId);
				digQueryParams.maxLength = Integer.parseInt(tokens[5]);
			}else if(digQueryParams.operation.equals("vertices") || digQueryParams.operation.equals("edges")){
				digQueryParams.expression = tokens[3];
			}
			return digQueryParams;
		}catch(Exception e){
			Logger.getLogger(DigQueryParams.class.getName()).log(Level.SEVERE, "Malformed query", e);
		}
		return null;
	}
	
	private static AbstractVertex getVertexForId(String storage, String id){
		try{
			Graph verticesGraph = Query.executeQuery("query " + storage + " vertices " + Settings.getProperty("storage_identifier")+":"+id, false);
			if(verticesGraph != null && verticesGraph.vertexSet().size() != 0){
				return verticesGraph.vertexSet().toArray(new AbstractVertex[]{})[0];
			}
		}catch(Exception e){
			Logger.getLogger(DigQueryParams.class.getName()).log(Level.SEVERE, "Failed to get vertex for id '" + id + "'", e);
		}
		return null;
	}

	public String getStorage() {
		return storage;
	}

	public String getOperation() {
		return operation;
	}

	public String getExpression() {
		return expression;
	}

	public AbstractVertex getVertex() {
		return vertex;
	}

	public AbstractVertex getSrcVertex() {
		return srcVertex;
	}

	public AbstractVertex getDstVertex() {
		return dstVertex;
	}

	public Integer getMaxLength() {
		return maxLength;
	}

	public Integer getDepth() {
		return depth;
	}

	public String getDirection() {
		return direction;
	}

	public String getTerminatingExpression() {
		return terminatingExpression;
	}

	public String getVertexId(){
		return vertexId;
	}
	
	public String getSrcVertexId(){
		return srcVertexId;
	}
	
	public String getDstVertexId(){
		return dstVertexId;
	}
}
