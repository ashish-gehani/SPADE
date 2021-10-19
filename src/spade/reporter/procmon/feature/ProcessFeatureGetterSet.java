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

import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class ProcessFeatureGetterSet{

	private static final ProcessFeatureGetter<Double> usedEdgeCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getFlowCounts().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> wasGeneratedByEdgeCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getFlowCounts().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> averageDurationBetweenUsedEdges = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getAverageDurationBetweenEvents().get();
		}
	};
	private static final ProcessFeatureGetter<Double> averageDurationBetweenWasGeneratedByEdges = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getAverageDurationBetweenEvents().get();
		}
	};
	private static final ProcessFeatureGetter<Double> averageTimeSpentInUsedEdges = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getAverageTimeSpentInFlows().get();
		}
	};
	private static final ProcessFeatureGetter<Double> averageTimeSpentInWasGeneratedByEdges = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getAverageTimeSpentInFlows().get();
		}
	};
	private static final ProcessFeatureGetter<Double> fileSystemUsedCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getFileSystemCount().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> fileSystemWasGeneratedByCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getFileSystemCount().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> processLifeDuration = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getLifeDuration();
		}
	};
	private static final ProcessFeatureGetter<Double> totalLengthInUsed = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getFlowSizes().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> totalLengthInWasGeneratedBy = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getFlowSizes().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> filesUsedCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getFilePathCount().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> filesWasGeneratedByCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getFilePathCount().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> extensionTypesUsedCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getExtensionCount().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> extensionTypesWasGeneratedByCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getExtensionCount().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> sensitiveFilesUsedCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getSensitiveExtensionCount().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> sensitiveFilesWasGeneratedByCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getSensitiveExtensionCount().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> wasTriggeredByCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasTriggeredBys().get();
		}
	};
	private static final ProcessFeatureGetter<Double> registryUsedCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getRegistryCount().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> registryWasGeneratedByCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getRegistryCount().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> networkUsedCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getNetworkCount().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> networkWasGeneratedByCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getNetworkCount().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> directoriesUsedCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getDirectoryPathCount().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> directoriesWasGeneratedByCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getDirectoryPathCount().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> usedEdgeCountBeginning = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getFlowCounts().getConditionalCount();
		}
	};
	private static final ProcessFeatureGetter<Double> wasGeneratedByEdgeCountBeginning = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getFlowCounts().getConditionalCount();
		}
	};
	private static final ProcessFeatureGetter<Boolean> processIsNew = new ProcessFeatureGetter<>(){
		public Boolean get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).isNew();
		}
	};
	private static final ProcessFeatureGetter<Double> registrySetInfoKeyCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getRegistrySetInfoKey().get();
		}
	};
	private static final ProcessFeatureGetter<Double> registrySetValueCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getRegistrySetValue().get();
		}
	};
	private static final ProcessFeatureGetter<Double> totalLengthInUsedBeginning = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getFlowSizes().getConditionalCount();
		}
	};
	private static final ProcessFeatureGetter<Double> totalLengthInWasGeneratedByBeginning = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getFlowSizes().getConditionalCount();
		}
	};
	private static final ProcessFeatureGetter<Double> filesUsedCountBeginning = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getFilePathCount().getConditionalCount();
		}
	};
	private static final ProcessFeatureGetter<Double> filesWasGeneratedByCountBeginning = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getFilePathCount().getConditionalCount();
		}
	};
	private static final ProcessFeatureGetter<Double> fileSystemUsedCountBeginning = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getFileSystemCount().getConditionalCount();
		}
	};
	private static final ProcessFeatureGetter<Double> fileSystemWasGeneratedByCountBeginning = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getFileSystemCount().getConditionalCount();
		}
	};
	private static final ProcessFeatureGetter<Double> registryUsedCountBeginning = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getRegistryCount().getConditionalCount();
		}
	};
	private static final ProcessFeatureGetter<Double> registryWasGeneratedByCountBeginning = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getRegistryCount().getConditionalCount();
		}
	};
	private static final ProcessFeatureGetter<Double> extensionTypesUsedCountBeginning = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getExtensionCount().getConditionalCount();
		}
	};
	private static final ProcessFeatureGetter<Double> extensionTypesWasGeneratedByCountBeginning = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getExtensionCount().getConditionalCount();
		}
	};
	private static final ProcessFeatureGetter<Double> sensitiveFilesUsedCountBeginning = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getSensitiveExtensionCount().getConditionalCount();
		}
	};
	private static final ProcessFeatureGetter<Double> sensitiveFilesWasGeneratedByCountBeginning = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getSensitiveExtensionCount().getConditionalCount();
		}
	};
	private static final ProcessFeatureGetter<Double> networkUsedCountBeginning = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getNetworkCount().getConditionalCount();
		}
	};
	private static final ProcessFeatureGetter<Double> networkWasGeneratedByCountBeginning = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getNetworkCount().getConditionalCount();
		}
	};
	private static final ProcessFeatureGetter<Double> directoriesUsedCountBeginning = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getDirectoryPathCount().getConditionalCount();
		}
	};
	private static final ProcessFeatureGetter<Double> directoriesWasGeneratedByCountBeginning = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getDirectoryPathCount().getConditionalCount();
		}
	};
	private static final ProcessFeatureGetter<Boolean> writesThenExecutes = new ProcessFeatureGetter<>(){
		public Boolean get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).isWritesThenExecutes();
		}
	};
	private static final ProcessFeatureGetter<Double> remoteHostsCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getNetworkCount().getCount()
					+ graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getNetworkCount().getCount();
		}
	};
	private static final ProcessFeatureGetter<Double> threadsCount = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getThreads().get();
		}
	};
	private static final ProcessFeatureGetter<String> processUser = new ProcessFeatureGetter<>(){
		public String get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getAgentName();
		}
	};
	private static final ProcessFeatureGetter<String> processName = new ProcessFeatureGetter<>(){
		public String get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getProcessName();
		}
	};
	private static final ProcessFeatureGetter<String> processCommandLine = new ProcessFeatureGetter<>(){
		public String get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getCommandLine();
		}
	};
	private static final ProcessFeatureGetter<String> processId = new ProcessFeatureGetter<>(){
		public String get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return processIdentifier.pid;
		}
	};
	private static final ProcessFeatureGetter<String> parentProcessId = new ProcessFeatureGetter<>(){
		public String get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getPpid();
		}
	};
	private static final ProcessFeatureGetter<Double> processLabel = new ProcessFeatureGetter<>(){
		public Double get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getLabel();
		}
	};
	private static final ProcessFeatureGetter<String> processStatus = new ProcessFeatureGetter<>(){
		public String get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getStatus();
		}
	};
	private static final ProcessFeatureGetter<Set<String>> childProcessNamesList = new ProcessFeatureGetter<>(){
		public Set<String> get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getChildProcessNames().get();
		}
	};
	private static final ProcessFeatureGetter<Set<String>> filesUsedList = new ProcessFeatureGetter<>(){
		public Set<String> get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getUsedFlowFeatures().getFilePathSet().get();
		}
	};
	private static final ProcessFeatureGetter<Set<String>> filesWasGeneratedByList = new ProcessFeatureGetter<>(){
		public Set<String> get(final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
			return graphFeatures.getProcessFeatures(processIdentifier).getWasGeneratedByFlowFeatures().getFilePathSet().get();
		}
	};

	private final TreeMap<String, ProcessFeatureGetter<?>> set = new TreeMap<>();

	public ProcessFeatureGetterSet(){
		set.put(GraphFeatureName.USED_EDGE_COUNT, usedEdgeCount);
		set.put(GraphFeatureName.WAS_GENERATED_BY_EDGE_COUNT, wasGeneratedByEdgeCount);
		set.put(GraphFeatureName.AVERAGE_DURATION_BETWEEN_USED_EDGES, averageDurationBetweenUsedEdges);
		set.put(GraphFeatureName.AVERAGE_DURATION_BETWEEN_WAS_GENERATED_BY_EDGES, averageDurationBetweenWasGeneratedByEdges);
		set.put(GraphFeatureName.AVERAGE_TIME_SPENT_IN_USED_EDGES, averageTimeSpentInUsedEdges);
		set.put(GraphFeatureName.AVERAGE_TIME_SPENT_IN_WAS_GENERATED_BY_EDGES, averageTimeSpentInWasGeneratedByEdges);
		set.put(GraphFeatureName.FILESYSTEM_USED_COUNT, fileSystemUsedCount);
		set.put(GraphFeatureName.FILESYSTEM_WAS_GENERATED_BY_COUNT, fileSystemWasGeneratedByCount);
		set.put(GraphFeatureName.PROCESS_LIFE_DURATION, processLifeDuration);
		set.put(GraphFeatureName.TOTAL_LENGTH_IN_USED, totalLengthInUsed);
		set.put(GraphFeatureName.TOTAL_LENGTH_IN_WAS_GENERATED_BY, totalLengthInWasGeneratedBy);
		set.put(GraphFeatureName.FILES_USED_COUNT, filesUsedCount);
		set.put(GraphFeatureName.FILES_WAS_GENERATED_BY_COUNT, filesWasGeneratedByCount);
		set.put(GraphFeatureName.EXTENSION_TYPES_USED_COUNT, extensionTypesUsedCount);
		set.put(GraphFeatureName.EXTENSION_TYPES_WAS_GENERATED_BY_COUNT, extensionTypesWasGeneratedByCount);
		set.put(GraphFeatureName.SENSITIVE_FILES_USED_COUNT, sensitiveFilesUsedCount);
		set.put(GraphFeatureName.SENSITIVE_FILES_WAS_GENERATED_BY_COUNT, sensitiveFilesWasGeneratedByCount);
		set.put(GraphFeatureName.WAS_TRIGGERED_BY_COUNT, wasTriggeredByCount);
		set.put(GraphFeatureName.REGISTRY_USED_COUNT, registryUsedCount);
		set.put(GraphFeatureName.REGISTRY_WAS_GENERATED_BY_COUNT, registryWasGeneratedByCount);
		set.put(GraphFeatureName.NETWORK_USED_COUNT, networkUsedCount);
		set.put(GraphFeatureName.NETWORK_WAS_GENERATED_BY_COUNT, networkWasGeneratedByCount);
		set.put(GraphFeatureName.DIRECTORIES_USED_COUNT, directoriesUsedCount);
		set.put(GraphFeatureName.DIRECTORIES_WAS_GENERATED_BY_COUNT, directoriesWasGeneratedByCount);
		set.put(GraphFeatureName.USED_EDGE_COUNT_BEGINNING, usedEdgeCountBeginning);
		set.put(GraphFeatureName.WAS_GENERATED_BY_EDGE_COUNT_BEGINNING, wasGeneratedByEdgeCountBeginning);
		set.put(GraphFeatureName.PROCESS_IS_NEW, processIsNew);
		set.put(GraphFeatureName.REGISTRY_SET_INFO_KEY_COUNT, registrySetInfoKeyCount);
		set.put(GraphFeatureName.REGISTRY_SET_VALUE_COUNT, registrySetValueCount);
		set.put(GraphFeatureName.TOTAL_LENGTH_IN_USED_BEGINNING, totalLengthInUsedBeginning);
		set.put(GraphFeatureName.TOTAL_LENGTH_IN_WAS_GENERATED_BY_BEGINNING, totalLengthInWasGeneratedByBeginning);
		set.put(GraphFeatureName.FILES_USED_COUNT_BEGINNING, filesUsedCountBeginning);
		set.put(GraphFeatureName.FILES_WAS_GENERATED_BY_COUNT_BEGINNING, filesWasGeneratedByCountBeginning);
		set.put(GraphFeatureName.FILESYSTEM_USED_COUNT_BEGINNING, fileSystemUsedCountBeginning);
		set.put(GraphFeatureName.FILESYSTEM_WAS_GENERATED_BY_COUNT_BEGINNING, fileSystemWasGeneratedByCountBeginning);
		set.put(GraphFeatureName.REGISTRY_USED_COUNT_BEGINNING, registryUsedCountBeginning);
		set.put(GraphFeatureName.REGISTRY_WAS_GENERATED_BY_COUNT_BEGINNING, registryWasGeneratedByCountBeginning);
		set.put(GraphFeatureName.EXTENSION_TYPES_USED_COUNT_BEGINNING, extensionTypesUsedCountBeginning);
		set.put(GraphFeatureName.EXTENSION_TYPES_WAS_GENERATED_BY_COUNT_BEGINNING, extensionTypesWasGeneratedByCountBeginning);
		set.put(GraphFeatureName.SENSITIVE_FILES_USED_COUNT_BEGINNING, sensitiveFilesUsedCountBeginning);
		set.put(GraphFeatureName.SENSITIVE_FILES_WAS_GENERATED_BY_COUNT_BEGINNING, sensitiveFilesWasGeneratedByCountBeginning);
		set.put(GraphFeatureName.NETWORK_USED_COUNT_BEGINNING, networkUsedCountBeginning);
		set.put(GraphFeatureName.NETWORK_WAS_GENERATED_BY_COUNT_BEGINNING, networkWasGeneratedByCountBeginning);
		set.put(GraphFeatureName.DIRECTORIES_USED_COUNT_BEGINNING, directoriesUsedCountBeginning);
		set.put(GraphFeatureName.DIRECTORIES_WAS_GENERATED_BY_COUNT_BEGINNING, directoriesWasGeneratedByCountBeginning);
		set.put(GraphFeatureName.WRITES_THEN_EXECUTES, writesThenExecutes);
		set.put(GraphFeatureName.REMOTE_HOSTS_COUNT, remoteHostsCount);
		set.put(GraphFeatureName.THREADS_COUNT, threadsCount);

		set.put(GraphFeatureName.PROCESS_USER, processUser);
		set.put(GraphFeatureName.PROCESS_NAME, processName);
		set.put(GraphFeatureName.PROCESS_COMMAND_LINE, processCommandLine);
		set.put(GraphFeatureName.PROCESS_ID, processId);
		set.put(GraphFeatureName.PROCESS_PARENT_ID, parentProcessId);
		set.put(GraphFeatureName.PROCESS_LABEL, processLabel);
		set.put(GraphFeatureName.PROCESS_STATUS, processStatus);
		set.put(GraphFeatureName.CHILD_PROCESS_NAMES_LIST, childProcessNamesList);
		set.put(GraphFeatureName.FILES_USED_LIST, filesUsedList);
		set.put(GraphFeatureName.FILES_WAS_GENERATED_BY_LIST, filesWasGeneratedByList);
	}

	public TreeSet<String> getNames(){
		return new TreeSet<>(set.keySet());
	}

	public Object get(final String name, final GraphFeatures graphFeatures, final ProcessIdentifier processIdentifier){
		return set.get(name).get(graphFeatures, processIdentifier);
	}
}
