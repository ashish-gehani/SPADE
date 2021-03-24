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
package spade.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import spade.client.QueryMetaData;

public class Query implements Serializable{

	private static final long serialVersionUID = -4867369163497687639L;

	public final String localName;
	public final String remoteName;

	public final String query;
	/*
	 * If the query is being sent from a local client then the query nonce should be
	 * null The server uses the null value to identify local query vs remote query
	 */
	public final String queryNonce;

	private boolean success = false;
	// only one of the following non-null at a time
	private Serializable error;
	private Serializable result;

	private List<Query> remoteSubqueries = new ArrayList<Query>();

	// Only required for local transformation of queries
	private final QueryMetaData queryMetaData = new QueryMetaData();

	public Query(String localName, String remoteName, String query, String queryNonce){
		this.localName = localName;
		this.remoteName = remoteName;
		this.query = query;
		this.queryNonce = queryNonce;
	}

	public void queryFailed(Serializable error){
		this.error = error;
		this.result = null;
		this.success = false;
	}

	public void querySucceeded(Serializable result){
		this.result = result;
		this.error = null;
		this.success = true;
	}

	public boolean wasQuerySuccessful(){
		return this.success;
	}

	public Serializable getError(){
		return this.error;
	}

	public Serializable getResult(){
		return this.result;
	}
	
	public void updateGraphResult(final Graph graph){
		this.result = graph;
	}

	public void addRemoteSubquery(Query subquery){
		if(subquery != null){
			remoteSubqueries.add(subquery);
		}
	}

	public List<Query> getRemoteSubqueries(){
		return new ArrayList<Query>(remoteSubqueries);
	}

	public QueryMetaData getQueryMetaData(){
		return queryMetaData;
	}

	@Override
	public String toString(){
		return "SPADEQuery ["
				+ "localName=" + localName 
				+ ", remoteName=" + remoteName 
				+ ", query=" + query
				+ ", queryNonce=" + queryNonce
				+ ", success=" + success
				+ ", error=" + error 
				+ ", result=" + result
				+ ", remoteSubqueries=" + remoteSubqueries 
				+ ", queryMetaData=" + queryMetaData + 
				"]";
	}

	public Set<Object> getAllResults(){
		Set<Object> results = new HashSet<Object>();

		LinkedList<Query> currentSet = new LinkedList<Query>();
		currentSet.add(this);
		while(!currentSet.isEmpty()){
			Query current = currentSet.poll();
			if(current.getResult() != null){
				results.add(current.getResult());
			}
			currentSet.addAll(current.getRemoteSubqueries());
		}

		return results;
	}

	public <T> Set<T> getAllResultsOfExactType(Class<T> clazz){
		Set<T> results = new HashSet<T>();

		Set<Query> marked = new HashSet<Query>();

		LinkedList<Query> currentSet = new LinkedList<Query>();
		currentSet.add(this);
		while(!currentSet.isEmpty()){
			Query current = currentSet.poll();
			marked.add(current);
			if(current.getResult() != null){
				if(clazz.equals(current.getResult().getClass())){
					results.add((T)(current.getResult()));
				}
			}
			currentSet.addAll(current.getRemoteSubqueries());
			currentSet.removeAll(marked);
		}

		return results;
	}
}
