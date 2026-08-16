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

import static spade.utility.feature.Count.ZERO;

import java.time.LocalDateTime;

import spade.utility.feature.Average;
import spade.utility.feature.AverageDurationBetweenEvents;
import spade.utility.feature.ConditionalCount.Condition;
import spade.utility.feature.CountAndConditionalCountPair;
import spade.utility.feature.UniqueItems;

public class FlowFeatures{

	private final CountAndConditionalCountPair flowCounts;
	private final AverageDurationBetweenEvents averageDurationBetweenEvents;
	private final CountAndConditionalCountPair flowSizes;
	private final Average averageTimeSpentInFlows;

	private final UniqueItems filePathSet;
	private final CountAndConditionalCountPair filePathCount;
	private final UniqueItems directoryPathSet;
	private final CountAndConditionalCountPair directoryPathCount;
	private final CountAndConditionalCountPair fileSystemCount;
	private final UniqueItems extensionSet;
	private final CountAndConditionalCountPair extensionCount;
	private final CountAndConditionalCountPair sensitiveExtensionCount;
	private final CountAndConditionalCountPair registryCount;
	private final CountAndConditionalCountPair networkCount;
	private final UniqueItems remoteHostSet;

	public FlowFeatures(final Condition condition){
		flowCounts = new CountAndConditionalCountPair(ZERO, ZERO, condition);
		averageDurationBetweenEvents = new AverageDurationBetweenEvents();
		flowSizes = new CountAndConditionalCountPair(ZERO, ZERO, condition);
		averageTimeSpentInFlows = new Average();

		filePathSet = new UniqueItems();
		filePathCount = new CountAndConditionalCountPair(ZERO, ZERO, condition);
		directoryPathSet = new UniqueItems();
		directoryPathCount = new CountAndConditionalCountPair(ZERO, ZERO, condition);
		fileSystemCount = new CountAndConditionalCountPair(ZERO, ZERO, condition);
		extensionSet = new UniqueItems();
		extensionCount = new CountAndConditionalCountPair(ZERO, ZERO, condition);
		sensitiveExtensionCount = new CountAndConditionalCountPair(ZERO, ZERO, condition);
		registryCount = new CountAndConditionalCountPair(ZERO, ZERO, condition);
		networkCount = new CountAndConditionalCountPair(ZERO, ZERO, condition);
		remoteHostSet = new UniqueItems();
	}

	public void updateEdge(final LocalDateTime eventTime, final double flowSize, final double timeSpentInFlow){
		flowCounts.update();
		averageDurationBetweenEvents.update(eventTime);
		flowSizes.update(flowSize);
		averageTimeSpentInFlows.update(timeSpentInFlow);
	}

	public void updateFileSystemPath(final String filePath, final String directoryPath){
		final boolean filePathAdded = filePathSet.update(filePath);
		if(filePathAdded){
			filePathCount.update();
		}
		final boolean directoryPathAdded = directoryPathSet.update(directoryPath);
		if(directoryPathAdded){
			directoryPathCount.update();
		}
		fileSystemCount.update();
	}

	public void updateExtension(final String extension, final boolean isSensitiveExtension){
		final boolean extensionAdded = extensionSet.update(extension);
		if(extensionAdded){
			extensionCount.update();
		}
		if(isSensitiveExtension){
			sensitiveExtensionCount.update();
		}
	}

	public void updateRegistry(){
		registryCount.update();
	}

	public void updateNetwork(final String remoteHost){
		networkCount.update();
		remoteHostSet.update(remoteHost);
	}

	public CountAndConditionalCountPair getFlowCounts(){
		return flowCounts;
	}

	public AverageDurationBetweenEvents getAverageDurationBetweenEvents(){
		return averageDurationBetweenEvents;
	}

	public CountAndConditionalCountPair getFlowSizes(){
		return flowSizes;
	}

	public Average getAverageTimeSpentInFlows(){
		return averageTimeSpentInFlows;
	}

	public UniqueItems getFilePathSet(){
		return filePathSet;
	}

	public CountAndConditionalCountPair getFilePathCount(){
		return filePathCount;
	}

	public UniqueItems getDirectoryPathSet(){
		return directoryPathSet;
	}

	public CountAndConditionalCountPair getDirectoryPathCount(){
		return directoryPathCount;
	}

	public CountAndConditionalCountPair getFileSystemCount(){
		return fileSystemCount;
	}

	public UniqueItems getExtensionSet(){
		return extensionSet;
	}

	public CountAndConditionalCountPair getExtensionCount(){
		return extensionCount;
	}

	public CountAndConditionalCountPair getSensitiveExtensionCount(){
		return sensitiveExtensionCount;
	}

	public CountAndConditionalCountPair getRegistryCount(){
		return registryCount;
	}

	public CountAndConditionalCountPair getNetworkCount(){
		return networkCount;
	}

	public UniqueItems getRemoteHostSet(){
		return remoteHostSet;
	}

}
