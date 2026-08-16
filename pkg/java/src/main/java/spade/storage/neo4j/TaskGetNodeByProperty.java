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
package spade.storage.neo4j;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import spade.storage.Neo4j;

public class TaskGetNodeByProperty extends StorageTask<Node>{

	private final Label nodeLabel;
	private final String propertyName, propertyValue;

	@Override
	public final String toString(){
		return "TaskGetNodeByProperty [nodeLabel="+nodeLabel+", propertyName="+propertyName+", propertyValue="+propertyValue+"]";
	}
	
	public TaskGetNodeByProperty(final Label nodeLabel, final String propertyName, final String propertyValue){
		super(false, false);
		this.nodeLabel = nodeLabel;
		this.propertyName = propertyName;
		this.propertyValue = propertyValue;
	}

	public final Node execute(final Neo4j storage, final Transaction transaction){
		try{
			storage.getStorageStats().startActionTimer(this.getClass().getSimpleName());
			final Node node = transaction.findNode(nodeLabel, propertyName, propertyValue);
			setResult(node);
			return node;
		}finally{
			storage.getStorageStats().stopActionTimer(this.getClass().getSimpleName());
		}
	}
}
