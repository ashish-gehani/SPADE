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

public class SPADEQuery implements Serializable{

	private static final long serialVersionUID = -4867369163497687639L;
	
	public final String localName;
	public final String remoteName;
	
	public final String query;
	/*
	 * If the query is being sent from a local client then the query nonce should be null
	 * The server uses the null value to identify local query vs remote query
	 */
	public final String queryNonce;
	
	private long querySentByClientAtMillis;
	private long queryReceivedByServerAtMillis;
	private long queryParsingStartedAtMillis;
	private long queryParsingCompletedAtMillis;
	
	private List<String> parsedProgramStatements = new ArrayList<String>();
	
	private long queryInstructionResolutionStartedAtMillis;
	private long queryInstructionResolutionCompletedAtMillis;
	
	private	List<QuickGrailInstruction> instructions = new ArrayList<QuickGrailInstruction>();
	
	private long queryExecutionStartedAtMillis;
	private long queryExecutionCompletedAtMillis;
	
	private boolean success = false;
	// only one of the following non-null at a time
	private Serializable error;
	private Serializable result;
	
	private long querySentBackToClientAtMillis;
	private long queryReceivedBackByClientAtMillis;
	
	private List<SPADEQuery> remoteSubqueries = new ArrayList<SPADEQuery>();
	
	public SPADEQuery(String localName, String remoteName,
			String query, String queryNonce){
		this.localName = localName;
		this.remoteName = remoteName;
		this.query = query;
		this.queryNonce = queryNonce;
	}

	public void setQuerySentByClientAtMillis(){ this.querySentByClientAtMillis = System.currentTimeMillis(); }
	public long getQuerySentByClientAtMillis(){ return this.querySentByClientAtMillis; }
	public void setQueryReceivedByServerAtMillis(){ this.queryReceivedByServerAtMillis = System.currentTimeMillis(); }
	public long getQueryReceivedByServerAtMillis(){ return this.queryReceivedByServerAtMillis; }
	public void setQueryParsingStartedAtMillis(){ this.queryParsingStartedAtMillis = System.currentTimeMillis(); }
	public long getQueryParsingStartedAtMillis(){ return this.queryParsingStartedAtMillis; }
	public void setQueryParsingCompletedAtMillis(List<String> parsedProgramStatements){
		this.queryParsingCompletedAtMillis = System.currentTimeMillis();
		this.parsedProgramStatements.clear();
		if(parsedProgramStatements != null){ this.parsedProgramStatements.addAll(parsedProgramStatements); }
	}
	public long getQueryParsingCompletedAtMillis(){ return this.queryParsingCompletedAtMillis; }
	public List<String> getParsedProgramStatements(){ return new ArrayList<String>(this.parsedProgramStatements); }
	
	public void setQueryInstructionResolutionStartedAtMillis(){ queryInstructionResolutionStartedAtMillis = System.currentTimeMillis(); }
	public long getQueryInstructionResolutionStartedAtMillis(){ return queryInstructionResolutionStartedAtMillis; }
	public void setQueryInstructionResolutionCompletedAtMillis(){ queryInstructionResolutionCompletedAtMillis = System.currentTimeMillis(); }
	public long getQueryInstructionResolutionCompletedAtMillis(){ return queryInstructionResolutionCompletedAtMillis; }
	public void setQueryExecutionStartedAtMillis(){ queryExecutionStartedAtMillis = System.currentTimeMillis(); }
	public long getQueryExecutionStartedAtMillis(){ return queryExecutionStartedAtMillis; }
	public void setQueryExecutionCompletedAtMillis(){ queryExecutionCompletedAtMillis = System.currentTimeMillis(); }
	public long getQueryExecutionCompletedAtMillis(){ return queryExecutionCompletedAtMillis; }
	
	public void queryFailed(Serializable error){ this.error = error; this.result = null; this.success = false; }
	public void querySucceeded(Serializable result){ this.result = result; this.error = null; this.success = true; }
	
	public boolean wasQuerySuccessful(){ return this.success; }
	public Serializable getError(){ return this.error; }
	public Serializable getResult(){ return this.result; }
	
	public void setQuerySentBackToClientAtMillis(){ querySentBackToClientAtMillis = System.currentTimeMillis(); }
	public long getQuerySentBackToClientAtMillis(){ return querySentBackToClientAtMillis; }
	public void setQueryReceivedBackByClientAtMillis(){ queryReceivedBackByClientAtMillis = System.currentTimeMillis(); }
	public long getQueryReceivedBackByClientAtMillis(){ return queryReceivedBackByClientAtMillis; }
	
	public void addQuickGrailInstruction(QuickGrailInstruction instruction){ if(instruction != null){ instructions.add(instruction); } }
	public List<QuickGrailInstruction> getQuickGrailInstructions(){ return new ArrayList<QuickGrailInstruction>(instructions); }
	
	public void addRemoteSubquery(SPADEQuery subquery){ if(subquery != null){ remoteSubqueries.add(subquery); } }
	public List<SPADEQuery> getRemoteSubqueries(){ return new ArrayList<SPADEQuery>(remoteSubqueries); }

	@Override
	public String toString(){
		return "SPADEQuery [localName=" + localName + ", remoteName=" + remoteName + ", query=" + query
				+ ", queryNonce=" + queryNonce + ", querySentByClientAtMillis=" + querySentByClientAtMillis
				+ ", queryReceivedByServerAtMillis=" + queryReceivedByServerAtMillis + ", queryParsingStartedAtMillis="
				+ queryParsingStartedAtMillis + ", queryParsingCompletedAtMillis=" + queryParsingCompletedAtMillis
				+ ", parsedProgramStatements=" + parsedProgramStatements
				+ ", queryInstructionResolutionStartedAtMillis=" + queryInstructionResolutionStartedAtMillis
				+ ", queryInstructionResolutionCompletedAtMillis=" + queryInstructionResolutionCompletedAtMillis
				+ ", instructions=" + instructions + ", queryExecutionStartedAtMillis=" + queryExecutionStartedAtMillis
				+ ", queryExecutionCompletedAtMillis=" + queryExecutionCompletedAtMillis + ", success=" + success
				+ ", error=" + error + ", result=" + result + ", querySentBackToClientAtMillis="
				+ querySentBackToClientAtMillis + ", queryReceivedBackByClientAtMillis="
				+ queryReceivedBackByClientAtMillis + ", remoteSubqueries=" + remoteSubqueries + "]";
	}
	
	public static class QuickGrailInstruction implements Serializable{
		private static final long serialVersionUID = 6556421048165337320L;
		
		public final String instruction;
		private long startedAtMillis;
		private long completedAtMillis;
		
		private boolean success = false;
		// only one of the following non-null at a time
		private Serializable error;
		private Serializable result;
		
		public QuickGrailInstruction(String instruction){
			this.instruction = instruction;
		}
		
		public void setStartedAtMillis(){ this.startedAtMillis = System.currentTimeMillis(); }
		public long getStartedAtMillis(){ return this.startedAtMillis; }
		
		public void setCompletedAtMillis(){ this.completedAtMillis = System.currentTimeMillis(); }
		public long getCompletedAtMillis(){ return this.completedAtMillis; }
		
		public boolean wasInstructionSuccessful(){ return success; }
		public Serializable getError(){ return this.error; }
		public Serializable getResult(){ return this.result; }
		
		public void instructionFailed(Serializable error){ this.error = error; this.result = null; this.success = false; }
		public void instructionSucceeded(Serializable result){ this.result = result; this.error = null; this.success = true; }

		@Override
		public String toString(){
			return "QuickGrailInstruction [instruction=" + instruction + ", startedAtMillis=" + startedAtMillis
					+ ", completedAtMillis=" + completedAtMillis + ", success=" + success + ", error=" + error
					+ ", result=" + result + "]";
		}

	}
	
	public Set<Object> getAllResults(){
		Set<Object> results = new HashSet<Object>();

		LinkedList<SPADEQuery> currentSet = new LinkedList<SPADEQuery>();
		currentSet.add(this);
		while(!currentSet.isEmpty()){
			SPADEQuery current = currentSet.poll();
			if(current.getResult() != null){
				results.add(current.getResult());
			}
			currentSet.addAll(current.getRemoteSubqueries());
		}

		return results;
	}
	
	public <T> Set<T> getAllResultsOfExactType(Class<T> clazz){
		Set<T> results = new HashSet<T>();

		Set<SPADEQuery> marked = new HashSet<SPADEQuery>();
		
		LinkedList<SPADEQuery> currentSet = new LinkedList<SPADEQuery>();
		currentSet.add(this);
		while(!currentSet.isEmpty()){
			SPADEQuery current = currentSet.poll();
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
