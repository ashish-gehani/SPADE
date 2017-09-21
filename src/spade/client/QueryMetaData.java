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

package spade.client;

import spade.core.AbstractVertex;

import java.util.Map;

public class QueryMetaData
{

	private String storage;
	private String operation;
	private String rootVertexHash;
	private AbstractVertex rootVertex;
	private String childVertexHash;
	private AbstractVertex childVertex;
	private String parentVertexHash;
	private AbstractVertex parentVertex;
	private Integer maxLength;
	private String direction;

	public QueryMetaData(Map<String, Object> queryMetaData)
	{
		storage = (String) queryMetaData.get("storage");
		operation = (String) queryMetaData.get("operation");
		rootVertex = (AbstractVertex) queryMetaData.get("rootVertex");
		rootVertexHash = (String) queryMetaData.get("rootVertexHash");
		childVertex = (AbstractVertex) queryMetaData.get("childVertex");
		childVertexHash = (String) queryMetaData.get("childVertexHash");
		parentVertex = (AbstractVertex) queryMetaData.get("parentVertex");
		parentVertexHash = (String) queryMetaData.get("parentVertexHash");
		maxLength = (Integer) queryMetaData.get("maxLength");
		direction = (String) queryMetaData.get("direction");
	}


	public String getStorage()
	{
		return storage;
	}

	public String getOperation()
	{
		return operation;
	}

	public String getRootVertexHash()
	{
		return rootVertexHash;
	}

	public AbstractVertex getRootVertex()
	{
		return rootVertex;
	}

	public String getChildVertexHash()
	{
		return childVertexHash;
	}

	public AbstractVertex getChildVertex()
	{
		return childVertex;
	}

	public String getParentVertexHash()
	{
		return parentVertexHash;
	}

	public AbstractVertex getParentVertex()
	{
		return parentVertex;
	}

	public Integer getMaxLength()
	{
		return maxLength;
	}

	public String getDirection()
	{
		return direction;
	}
}
