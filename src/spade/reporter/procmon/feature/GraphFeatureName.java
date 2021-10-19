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

public class GraphFeatureName{

	public static final String
		FILE_PATH = "FileName"
		, PROCESSES_TAINTED_BY_FILE_COUNT = "CountTaintedProcesses"
		, WAS_GENERATED_BY_OPERATIONS_ON_FILE_LIST = "OperationTypes"
		, PROCESSES_TAINTED_BY_FILE_LIST = "taintedProcesses"
		;

	public static final String
		PROCESS_USER = "User"
		, PROCESS_NAME = "name"
		, PROCESS_COMMAND_LINE = "commandline"
		, PROCESS_ID = "pid"
		, PROCESS_PARENT_ID = "ppid"
		, PROCESS_LABEL = "label"
		, PROCESS_STATUS = "state"
		, CHILD_PROCESS_NAMES_LIST = "NameTriggered"
		, FILES_USED_LIST = "listOfUsedFiles"
		, FILES_WAS_GENERATED_BY_LIST = "listOfWgbFiles"
		;

	public static final String
		USED_EDGE_COUNT = "countUsed"
		, WAS_GENERATED_BY_EDGE_COUNT = "countWgb"
		, AVERAGE_DURATION_BETWEEN_USED_EDGES = "avgDurationBetweenTwoUsed"
		, AVERAGE_DURATION_BETWEEN_WAS_GENERATED_BY_EDGES = "avgDurationBetweenTwoWgb"
		, AVERAGE_TIME_SPENT_IN_USED_EDGES = "avgDurationUsed"
		, AVERAGE_TIME_SPENT_IN_WAS_GENERATED_BY_EDGES = "avgDurationWgb"
		, FILESYSTEM_USED_COUNT = "countFilesystemUsed"
		, FILESYSTEM_WAS_GENERATED_BY_COUNT = "countFilesystemWgb"
		, PROCESS_LIFE_DURATION = "lifeDuration"
		, TOTAL_LENGTH_IN_USED = "totalLengthRead"
		, TOTAL_LENGTH_IN_WAS_GENERATED_BY = "totalLengthWritten"
		, FILES_USED_COUNT = "countOfUsedFiles"
		, FILES_WAS_GENERATED_BY_COUNT = "countOfWgbFiles"
		, EXTENSION_TYPES_USED_COUNT = "countExtensionTypeUsed"
		, EXTENSION_TYPES_WAS_GENERATED_BY_COUNT = "countExtensionTypeWgb"
		, SENSITIVE_FILES_USED_COUNT = "countExeDatDllBinUsed"
		, SENSITIVE_FILES_WAS_GENERATED_BY_COUNT = "countExeDatDllBinWgb"
		, WAS_TRIGGERED_BY_COUNT = "countWasTriggeredBy"
		, REGISTRY_USED_COUNT = "countRegistryUsed"
		, REGISTRY_WAS_GENERATED_BY_COUNT = "countRegistryWgb"
		, NETWORK_USED_COUNT = "countNetworkReceive"
		, NETWORK_WAS_GENERATED_BY_COUNT = "countNetworkSend"
		, DIRECTORIES_USED_COUNT = "countOfDirectoriesUsed"
		, DIRECTORIES_WAS_GENERATED_BY_COUNT = "countOfDirectoriesWgb"
		, USED_EDGE_COUNT_BEGINNING = appendBeginning(USED_EDGE_COUNT)
		, WAS_GENERATED_BY_EDGE_COUNT_BEGINNING = appendBeginning(WAS_GENERATED_BY_EDGE_COUNT)
		, PROCESS_IS_NEW = "isNew"
		, REGISTRY_SET_INFO_KEY_COUNT = "countRegSetInfoKey"
		, REGISTRY_SET_VALUE_COUNT = "countRegSetValue"
		, TOTAL_LENGTH_IN_USED_BEGINNING = appendBeginning(TOTAL_LENGTH_IN_USED)
		, TOTAL_LENGTH_IN_WAS_GENERATED_BY_BEGINNING = appendBeginning(TOTAL_LENGTH_IN_WAS_GENERATED_BY)
		, FILES_USED_COUNT_BEGINNING = appendBeginning(FILES_USED_COUNT)
		, FILES_WAS_GENERATED_BY_COUNT_BEGINNING = appendBeginning(FILES_WAS_GENERATED_BY_COUNT)
		, FILESYSTEM_USED_COUNT_BEGINNING = appendBeginning(FILESYSTEM_USED_COUNT)
		, FILESYSTEM_WAS_GENERATED_BY_COUNT_BEGINNING = appendBeginning(FILESYSTEM_WAS_GENERATED_BY_COUNT)
		, REGISTRY_USED_COUNT_BEGINNING = appendBeginning(REGISTRY_USED_COUNT)
		, REGISTRY_WAS_GENERATED_BY_COUNT_BEGINNING = appendBeginning(REGISTRY_WAS_GENERATED_BY_COUNT)
		, EXTENSION_TYPES_USED_COUNT_BEGINNING = appendBeginning(EXTENSION_TYPES_USED_COUNT)
		, EXTENSION_TYPES_WAS_GENERATED_BY_COUNT_BEGINNING = appendBeginning(EXTENSION_TYPES_WAS_GENERATED_BY_COUNT)
		, SENSITIVE_FILES_USED_COUNT_BEGINNING = appendBeginning(SENSITIVE_FILES_USED_COUNT)
		, SENSITIVE_FILES_WAS_GENERATED_BY_COUNT_BEGINNING = appendBeginning(SENSITIVE_FILES_WAS_GENERATED_BY_COUNT)
		, NETWORK_USED_COUNT_BEGINNING = appendBeginning(NETWORK_USED_COUNT)
		, NETWORK_WAS_GENERATED_BY_COUNT_BEGINNING = appendBeginning(NETWORK_WAS_GENERATED_BY_COUNT)
		, DIRECTORIES_USED_COUNT_BEGINNING = appendBeginning(DIRECTORIES_USED_COUNT)
		, DIRECTORIES_WAS_GENERATED_BY_COUNT_BEGINNING = appendBeginning(DIRECTORIES_WAS_GENERATED_BY_COUNT)
		, WRITES_THEN_EXECUTES = "writeThenExecutes"
		, REMOTE_HOSTS_COUNT = "countRemoteHost"
		, THREADS_COUNT = "countThread"
		;

	private static String appendBeginning(final String name){
		return name + "Beginning";
	}
}
