/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International

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
package spade.reporter.procmon.feature;

import java.time.Duration;
import java.time.LocalDateTime;

import spade.utility.feature.ConditionalCount.Condition;
import spade.utility.feature.Count;
import spade.utility.feature.UniqueItems;

public class ProcessFeatures{

	private LocalDateTime firstActivityDateTime = null;
	private double lifeDuration = 0; // nanoseconds

	private final UsedFlowFeatures usedFlowFeatures;
	private final WasGeneratedByFlowFeatures wasGeneratedByFlowFeatures;

	private String agentName = null;

	private final UniqueItems childProcessNames = new UniqueItems();
	private Count wasTriggeredBys = new Count(Count.ZERO);
	private Count threads = new Count(Count.ZERO);
	private boolean isNew = false;
	private boolean writesThenExecutes = false;

	private String ppid;
	private String processName;
	private String commandLine;
	private String bigHashCode;

	public ProcessFeatures(final double beginningThreshold){
		final Condition beginningThresholdCondition = new Condition(){
			@Override
			public boolean isSatisfied(){
				return getLifeDuration() < beginningThreshold;
			}
		};
		this.usedFlowFeatures = new UsedFlowFeatures(beginningThresholdCondition);
		this.wasGeneratedByFlowFeatures = new WasGeneratedByFlowFeatures(beginningThresholdCondition);
	}

	public UsedFlowFeatures getUsedFlowFeatures(){
		return usedFlowFeatures;
	}

	public WasGeneratedByFlowFeatures getWasGeneratedByFlowFeatures(){
		return wasGeneratedByFlowFeatures;
	}

	public void updateLifeDuration(final LocalDateTime dateTime, double scaleTime){
		if(firstActivityDateTime == null){
			firstActivityDateTime = dateTime;
		}
		if(firstActivityDateTime != null && dateTime != null){
			lifeDuration = Math.abs(Duration.between(dateTime, firstActivityDateTime).toNanos());
		}
	}

	public double getLifeDuration(){
		return lifeDuration;
	}

	public final void setAgentName(final String agentName){
		this.agentName = agentName;
	}

	public final void updateChildProcess(final String childProcessName){
		this.childProcessNames.update(childProcessName);
		this.wasTriggeredBys.update();
	}

	public final void updateThreads(){
		this.threads.update();
	}

	public final void setIsNew(){
		this.isNew = true;
	}

	public final boolean filePathWasGeneratedByProcess(final String filePath){
		return wasGeneratedByFlowFeatures.containsExecutableFilePath(filePath);
	}

	public final void setWritesThenExecutes(){
		this.writesThenExecutes = true;
	}

	public final void setPpid(final String ppid){
		this.ppid = ppid;
	}

	public final void setProcessName(final String processName){
		this.processName = processName;
	}

	public final void setCommandLine(final String commandLine){
		this.commandLine = commandLine;
	}

	public final void setVertexBigHashCode(final String bigHashCode){
		this.bigHashCode = bigHashCode;
	}

	public LocalDateTime getFirstActivityDateTime(){
		return firstActivityDateTime;
	}

	public String getAgentName(){
		return agentName;
	}

	public UniqueItems getChildProcessNames(){
		return childProcessNames;
	}

	public Count getWasTriggeredBys(){
		return wasTriggeredBys;
	}

	public Count getThreads(){
		return threads;
	}

	public boolean isNew(){
		return isNew;
	}

	public boolean isWritesThenExecutes(){
		return writesThenExecutes;
	}

	public String getPpid(){
		return ppid;
	}

	public String getProcessName(){
		return processName;
	}

	public String getCommandLine(){
		return commandLine;
	}

	public String getBigHashCode(){
		return bigHashCode;
	}

}
