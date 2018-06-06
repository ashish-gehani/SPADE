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

import spade.utility.CommonFunctions;

// Global Audit reporter flags
public class Globals{

	// Currently only artifact related arguments
	
	public static final String unixSocketsKey = "unixSockets",
					versionNetworkSocketsKey = "versionNetworkSockets",
					versionFilesKey = "versionFiles",
					versionMemorysKey = "versionMemorys",
					versionNamedPipesKey = "versionNamedPipes",
					versionUnnamedPipesKey = "versionUnnamedPipes",
					versionUnknownsKey = "versionUnknowns",
					versionUnixSocketsKey = "versionUnixSockets",
					versionUnnamedUnixSocketPairsKey = "versionUnnamedUnixSocketPairs",
					versionUnnamedNetworkSocketPairsKey = "versionUnnamedNetworkSocketPairs",
					versionsKey = "versions",
					epochsKey = "epochs",
					permissionsKey = "permissions";
	
	public final boolean unixSockets,
					versionNetworkSockets,
					versionFiles,
					versionMemorys,
					versionNamedPipes,
					versionUnnamedPipes,
					versionUnknowns,
					versionUnixSockets,
					versionUnnamedUnixSocketPairs,
					versionUnnamedNetworkSocketPairs,
					versions,
					epochs,
					permissions;
	public final boolean keepingArtifactPropertiesMap;
	
	private Globals(boolean unixSockets, boolean versionNetworkSockets, boolean versionFiles, 
			boolean versionMemorys, boolean versionNamedPipes, boolean versionUnnamedPipes,
			boolean versionUnknowns, boolean versionUnixSockets, boolean versionUnnamedUnixSocketPairs,
			boolean versionUnnamedNetworkSocketPairs, boolean versions, boolean epochs, boolean permissions){
		this.unixSockets = unixSockets;
		this.versionNetworkSockets = versionNetworkSockets;
		this.versionFiles = versionFiles;
		this.versionMemorys = versionMemorys;
		this.versionNamedPipes = versionNamedPipes;
		this.versionUnnamedPipes = versionUnnamedPipes;
		this.versionUnknowns = versionUnknowns;
		this.versionUnixSockets = versionUnixSockets;
		this.versionUnnamedUnixSocketPairs = versionUnnamedUnixSocketPairs;
		this.versionUnnamedNetworkSocketPairs = versionUnnamedNetworkSocketPairs;
		this.versions = versions;
		this.epochs = epochs;
		this.permissions = permissions;
		this.keepingArtifactPropertiesMap = versions || epochs || permissions;
	}
	
	public String toString(){
		return String.format("Audit flags (Globals): '%s'='%s', '%s'='%s', '%s'='%s', '%s'='%s', '%s'='%s',"
				+ " '%s'='%s', '%s'='%s', '%s'='%s', '%s'='%s', '%s'='%s',"
				+ " '%s'='%s', '%s'='%s', '%s'='%s'", 
				unixSocketsKey, unixSockets, versionNetworkSocketsKey, versionNetworkSockets,
				versionFilesKey, versionFiles, versionMemorysKey, versionMemorys,
				versionNamedPipesKey, versionNamedPipes, versionUnnamedPipesKey, versionUnnamedPipes,
				versionUnknownsKey, versionUnknowns, versionUnixSocketsKey, versionUnixSockets,
				versionUnnamedUnixSocketPairsKey, versionUnnamedUnixSocketPairs, 
				versionUnnamedNetworkSocketPairsKey, versionUnnamedNetworkSocketPairs,
				versionsKey, versions, epochsKey, epochs,
				permissionsKey, permissions);
	}
	
	public static Globals parseArguments(String arguments) throws Exception{
		Map<String, String> map = CommonFunctions.parseKeyValPairs(arguments);
		return parseArguments(map);
	}
	
	public static Globals parseArguments(Map<String, String> map) throws Exception{
		// Default values
		return new Globals(parseBooleanArgument(map, unixSocketsKey, false),
				parseBooleanArgument(map, versionNetworkSocketsKey, false),
				parseBooleanArgument(map, versionFilesKey, true),
				parseBooleanArgument(map, versionMemorysKey, true),
				parseBooleanArgument(map, versionNamedPipesKey, true),
				parseBooleanArgument(map, versionUnnamedPipesKey, true),
				parseBooleanArgument(map, versionUnknownsKey, true),
				parseBooleanArgument(map, versionUnixSocketsKey, true),
				parseBooleanArgument(map, versionUnnamedUnixSocketPairsKey, true),
				parseBooleanArgument(map, versionUnnamedNetworkSocketPairsKey, false),
				parseBooleanArgument(map, versionsKey, true),
				parseBooleanArgument(map, epochsKey, true),
				parseBooleanArgument(map, permissionsKey, true));
	}
	
	private static Boolean parseBooleanArgument(Map<String, String> map, String key, Boolean defaultValue)
								throws Exception{
		if(map == null){
			throw new Exception("NULL arguments map");
		}else{
			if(CommonFunctions.isNullOrEmpty(key)){
				throw new Exception("NULL/Empty argument key: " + "'"+key+"'");
			}else{
				String value = map.get(key);
				if(value == null){
					return defaultValue;
				}else{
					if(value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("1")
							|| value.equalsIgnoreCase("on")){
						return true;
					}else if(value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no") || value.equalsIgnoreCase("0")
							|| value.equalsIgnoreCase("off")){
						return false;
					}else{
						throw new Exception("Invalid value for boolean argument '"+key+"': '"+value+"'");
					}
				}
			}
		}
	}
}
