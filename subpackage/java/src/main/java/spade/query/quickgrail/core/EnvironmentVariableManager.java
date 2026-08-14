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
package spade.query.quickgrail.core;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import spade.core.Settings;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;

public class EnvironmentVariableManager{

	private final static String CONSTANT_UNDEFINED = "UNDEFINED";

	public static enum Name{
		maxDepth(Integer.class), 
		limit(Integer.class),
		precision(Integer.class),
		exportLimit(Integer.class);
		
		private final Class<?> type;
		private Name(final Class<?> type){
			this.type = type;
		}
		public Class<?> getType(){
			return this.type;
		}
	}

	///////////////////////////

	private final Map<String, EnvironmentVariable> envVars = new TreeMap<>();

	public final void initialize(){
		envVars.clear();
		
		final Class<?> configClass = this.getClass();
		final String configFilePath = Settings.getDefaultConfigFilePath(configClass);
		final Map<String, String> configMap;
		try{
			configMap = FileUtility.readConfigFileAsKeyValueMap(configFilePath, "=");
		}catch(Exception e){
			throw new RuntimeException("Failed to read default environment variables: " + configFilePath, e);
		}
		for(final Name envNameEnum : Name.values()){
			final String envVarName = envNameEnum.name();
			final EnvironmentVariable envVar = new EnvironmentVariable(envVarName, envNameEnum.getType());
			final String valueInConfig = configMap.get(envVarName);
			if(valueInConfig != null){
				try{
					envVar.setValue(valueInConfig);
				}catch(Exception e){
					throw new RuntimeException(
							"Failed to set default environment variable '" + envVarName + "': " + configFilePath, e);
				}
			}
			envVars.put(envVarName, envVar);
		}
	}

	public EnvironmentVariable get(final Name name){
		if(name == null){
			return null;
		}
		return get(name.name());
	}

	public EnvironmentVariable get(final String name){
		return envVars.get(name);
	}

	public Set<EnvironmentVariable> getAll(){
		return new TreeSet<>(envVars.values());
	}

	///////////////////////////

	public static boolean isUndefinedConstant(final String value){
		return getUndefinedConstant().equalsIgnoreCase(value);
	}

	public static String getUndefinedConstant(){
		return CONSTANT_UNDEFINED;
	}

	public static String[] getAllNames(){
		return HelperFunctions.getEnumNames(Name.class, false);
	}
}
