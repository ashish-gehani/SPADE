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
package spade.reporter.audit;

import java.util.Map;

import spade.utility.ArgumentFunctions;

// Global Audit reporter flags
public class ArtifactConfiguration{

	// Currently only artifact related arguments

	public static final String 
					unixSocketsKey = "unixSockets",
					versionNetworkSocketsKey = "versionNetworkSockets",
					versionFilesKey = "versionFiles",
					versionMemorysKey = "versionMemorys",
					versionNamedPipesKey = "versionNamedPipes",
					versionUnnamedPipesKey = "versionUnnamedPipes",
					versionUnknownsKey = "versionUnknowns",
					versionUnixSocketsKey = "versionUnixSockets",
					versionUnnamedUnixSocketPairsKey = "versionUnixSocketPairs",
					versionUnnamedNetworkSocketPairsKey = "versionNetworkSocketPairs",
					versionSysVMessageQueueKey = "versionSysVMessageQueue",
					versionSysVSharedMemoryKey = "versionSysVSharedMemory",
					versionPosixMessageQueueKey = "versionPosixMessageQueue",
					versionsKey = "versions",
					epochsKey = "epochs",
					permissionsKey = "permissions";

	private boolean unixSockets,
					versionNetworkSockets,
					versionFiles,
					versionMemorys,
					versionNamedPipes,
					versionUnnamedPipes,
					versionUnknowns,
					versionUnixSockets,
					versionUnnamedUnixSocketPairs,
					versionUnnamedNetworkSocketPairs,
					versionSysVMessageQueue,
					versionSysVSharedMemory,
					versionPosixMessageQueue,
					versions,
					epochs,
					permissions;
	private boolean keepingArtifactPropertiesMap;

	private ArtifactConfiguration(){}

	public String toString(){
		return "ArtifactConfiguration ["
				+ unixSocketsKey + "=" + unixSockets
				+ ", " + versionNetworkSocketsKey + "=" + versionNetworkSockets
				+ ", " + versionFilesKey + "=" + versionFiles
				+ ", " + versionMemorysKey + "=" + versionMemorys
				+ ", " + versionNamedPipesKey + "=" + versionNamedPipes
				+ ", " + versionUnnamedPipesKey + "=" + versionUnnamedPipes
				+ ", " + versionUnknownsKey + "=" + versionUnknowns
				+ ", " + versionUnixSocketsKey + "=" + versionUnixSockets
				+ ", " + versionUnnamedUnixSocketPairsKey + "=" + versionUnnamedUnixSocketPairs
				+ ", " + versionUnnamedNetworkSocketPairsKey + "=" + versionUnnamedNetworkSocketPairs
				+ ", " + versionSysVMessageQueueKey + "=" + versionSysVMessageQueue
				+ ", " + versionSysVSharedMemoryKey + "=" + versionSysVSharedMemory
				+ ", " + versionPosixMessageQueueKey + "=" + versionPosixMessageQueue
				+ ", " + versionsKey + "=" + versions
				+ ", " + epochsKey + "=" + epochs
				+ ", " + permissionsKey + "=" + permissions
				+ ", " + "keepingArtifactPropertiesMap" + "=" + keepingArtifactPropertiesMap
				+ "]";
	}

	public static ArtifactConfiguration instance(final Map<String, String> map) throws Exception{
		final ArtifactConfiguration instance = new ArtifactConfiguration();
		instance.unixSockets = ArgumentFunctions.mustParseBoolean(unixSocketsKey, map);
		instance.versionNetworkSockets = ArgumentFunctions.mustParseBoolean(versionNetworkSocketsKey, map);
		instance.versionFiles = ArgumentFunctions.mustParseBoolean(versionFilesKey, map);
		instance.versionMemorys = ArgumentFunctions.mustParseBoolean(versionMemorysKey, map);
		instance.versionNamedPipes = ArgumentFunctions.mustParseBoolean(versionNamedPipesKey, map);
		instance.versionUnnamedPipes = ArgumentFunctions.mustParseBoolean(versionUnnamedPipesKey, map);
		instance.versionUnknowns = ArgumentFunctions.mustParseBoolean(versionUnknownsKey, map);
		instance.versionUnixSockets = ArgumentFunctions.mustParseBoolean(versionUnixSocketsKey, map);
		instance.versionUnnamedUnixSocketPairs = ArgumentFunctions.mustParseBoolean(versionUnnamedUnixSocketPairsKey, map);
		instance.versionUnnamedNetworkSocketPairs = ArgumentFunctions.mustParseBoolean(versionUnnamedNetworkSocketPairsKey, map);
		instance.versionSysVMessageQueue = ArgumentFunctions.mustParseBoolean(versionSysVMessageQueueKey, map);
		instance.versionSysVSharedMemory = ArgumentFunctions.mustParseBoolean(versionSysVSharedMemoryKey, map);
		instance.versionPosixMessageQueue = ArgumentFunctions.mustParseBoolean(versionPosixMessageQueueKey, map);
		instance.versions = ArgumentFunctions.mustParseBoolean(versionsKey, map);
		instance.epochs = ArgumentFunctions.mustParseBoolean(epochsKey, map);
		instance.permissions = ArgumentFunctions.mustParseBoolean(permissionsKey, map);
		instance.keepingArtifactPropertiesMap = instance.versions || instance.epochs || instance.permissions;
		return instance;
	}

	public boolean isUnixSockets(){
		return unixSockets;
	}

	public boolean isVersionNetworkSockets(){
		return versionNetworkSockets;
	}

	public boolean isVersionFiles(){
		return versionFiles;
	}

	public boolean isVersionMemorys(){
		return versionMemorys;
	}

	public boolean isVersionNamedPipes(){
		return versionNamedPipes;
	}

	public boolean isVersionUnnamedPipes(){
		return versionUnnamedPipes;
	}

	public boolean isVersionUnknowns(){
		return versionUnknowns;
	}

	public boolean isVersionUnixSockets(){
		return versionUnixSockets;
	}

	public boolean isVersionUnnamedUnixSocketPairs(){
		return versionUnnamedUnixSocketPairs;
	}

	public boolean isVersionUnnamedNetworkSocketPairs(){
		return versionUnnamedNetworkSocketPairs;
	}

	public boolean isVersionSysVMessageQueue(){
		return versionSysVMessageQueue;
	}

	public boolean isVersionSysVSharedMemory(){
		return versionSysVSharedMemory;
	}

	public boolean isVersionPosixMessageQueue(){
		return versionPosixMessageQueue;
	}

	public boolean isVersions(){
		return versions;
	}

	public boolean isEpochs(){
		return epochs;
	}

	public boolean isPermissions(){
		return permissions;
	}

	public boolean isKeepingArtifactPropertiesMap(){
		return keepingArtifactPropertiesMap;
	}

}
