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
package spade.reporter.audit;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import spade.core.Settings;
import spade.utility.ArgumentFunctions;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;

public class KernelModuleConfiguration{

	public static final String 
			keyKernelModuleMainPath = "kernelModuleMain",
			keyKernelModuleControllerPath = "kernelModuleController",
			keyHarden = "harden",
			keyLocalEndpoints = "localEndpoints", 
			keyHandleLocalEndpoints = "handleLocalEndpoints",
			keyHardenProcesses = "hardenProcesses",
			keyNetworkAddressTranslation = "networkAddressTranslation",
			keyNetfilterHooksLogCT = "netfilterHooksLogCT",
			keyNetfilterHooksUser = "netfilterHooksUser",
			keyHandleNetworkAddressTranslation = "handleNetworkAddressTranslation";

			private String kernelModuleMainPath, kernelModuleControllerPath;
	private boolean harden, localEndpoints;
	private Boolean handleLocalEndpoints;
	private Set<String> hardenProcesses = new HashSet<String>();
	private boolean networkAddressTranslation;
	private boolean netfilterHooksLogCT;
	private boolean netfilterHooksUser;
	private boolean handleNetworkAddressTranslation;

	private KernelModuleConfiguration(final String kernelModuleMainPath, final String kernelModuleControllerPath,
			final boolean harden, final boolean localEndpoints,
			final Boolean handleLocalEndpoints, final Set<String> hardenProcesses,
			final boolean networkAddressTranslation, final boolean netfilterHooksLogCT, 
			final boolean netfilterHooksUser, final boolean handleNetworkAddressTranslation){
		this.kernelModuleMainPath = kernelModuleMainPath;
		this.kernelModuleControllerPath = kernelModuleControllerPath;
		this.harden = harden;
		this.localEndpoints = localEndpoints;
		this.handleLocalEndpoints = handleLocalEndpoints;
		this.hardenProcesses.addAll(hardenProcesses);
		this.networkAddressTranslation = networkAddressTranslation;
		this.netfilterHooksLogCT = netfilterHooksLogCT;
		this.netfilterHooksUser = netfilterHooksUser;
		this.handleNetworkAddressTranslation = handleNetworkAddressTranslation;
	}

	public static KernelModuleConfiguration instance(final String configFilePath, final boolean isLive)
			throws Exception{
		final Map<String, String> map;
		try{
			map = FileUtility.readConfigFileAsKeyValueMap(configFilePath, "=");
		}catch(Exception e){
			throw new Exception("Failed to read/parse kernel module config file: '" + configFilePath + "'", e);
		}
		return instance(map, isLive);
	}

	public static KernelModuleConfiguration instance(final Map<String, String> map, final boolean isLive)
			throws Exception{
		String valueKernelModuleMainPath = map.get(keyKernelModuleMainPath);
		String valueKernelModuleControllerPath = map.get(keyKernelModuleControllerPath);
		
		final String kernelModuleMainPath;
		final String kernelModuleControllerPath;
		final boolean localEndpoints;
		final boolean harden;
		final Boolean handleLocalEndpoints;
		final Set<String> hardenProcesses = new HashSet<String>();
		final boolean networkAddressTranslation;
		final boolean netfilterHooksLogCT;
		final boolean netfilterHooksUser;
		final boolean handleNetworkAddressTranslation;

		if(isLive){
			final String valueLocalEndpoints = map.get(keyLocalEndpoints);
			if(HelperFunctions.isNullOrEmpty(valueLocalEndpoints)){
				/*
				 * If local endpoints is not defined then check if the kernel module files
				 * exist. If the kernel module files exist then set local endpoints to true
				 * otherwise false.
				 */
				valueKernelModuleMainPath = Settings.getPathRelativeToSPADERootIfNotAbsolute(valueKernelModuleMainPath);
				valueKernelModuleControllerPath = Settings.getPathRelativeToSPADERootIfNotAbsolute(valueKernelModuleControllerPath);
				if(isKernelModuleFileReadableIfExists(keyKernelModuleMainPath, valueKernelModuleMainPath)
						&& isKernelModuleFileReadableIfExists(keyKernelModuleControllerPath, valueKernelModuleControllerPath)){
					localEndpoints = true;
					kernelModuleMainPath = valueKernelModuleMainPath;
					kernelModuleControllerPath = valueKernelModuleControllerPath;
				}else{
					localEndpoints = false;
					kernelModuleMainPath = kernelModuleControllerPath = null;
				}
			}else{
				/*
				 * If local endpoints is true then check if the kernel module files are valid
				 * paths otherwise no need to check.
				 */
				localEndpoints = ArgumentFunctions.mustParseBoolean(keyLocalEndpoints, map);
				if(localEndpoints){
					valueKernelModuleMainPath = Settings.getPathRelativeToSPADERootIfNotAbsolute(valueKernelModuleMainPath);
					if(!isKernelModuleFileReadableIfExists(keyKernelModuleMainPath, valueKernelModuleMainPath)){
						throw new Exception("The kernel module's path specified by '" + keyKernelModuleMainPath
								+ "' does not exist at path: '" + valueKernelModuleMainPath + "'");
					}
					kernelModuleMainPath = valueKernelModuleMainPath;
					valueKernelModuleControllerPath = Settings.getPathRelativeToSPADERootIfNotAbsolute(valueKernelModuleControllerPath);
					if(!isKernelModuleFileReadableIfExists(keyKernelModuleControllerPath, valueKernelModuleControllerPath)){
						throw new Exception("The kernel module's path specified by '" + keyKernelModuleControllerPath
								+ "' does not exist at path: '" + valueKernelModuleControllerPath + "'");
					}
					kernelModuleControllerPath = valueKernelModuleControllerPath;
				}else{
					kernelModuleMainPath = kernelModuleControllerPath = null;
				}
			}

			/*
			 * If we are live and local endpoints was true then get the value of harden
			 * otherwise no need to get it.
			 */
			if(localEndpoints){
				harden = ArgumentFunctions.mustParseBoolean(keyHarden, map);
				networkAddressTranslation = ArgumentFunctions.mustParseBoolean(keyNetworkAddressTranslation, map);
			}else{
				harden = false;
				networkAddressTranslation = false;
			}
			
			if(networkAddressTranslation){
				netfilterHooksLogCT = ArgumentFunctions.mustParseBoolean(keyNetfilterHooksLogCT, map);
				netfilterHooksUser = ArgumentFunctions.mustParseBoolean(keyNetfilterHooksUser, map);
				final String valueHandleNetworkAddressTranslation = map.get(keyHandleNetworkAddressTranslation);
				if(HelperFunctions.isNullOrEmpty(valueHandleNetworkAddressTranslation)){
					// If not specified then use the value of the netfilter hooks
					handleNetworkAddressTranslation = networkAddressTranslation;
				}else{
					// Use the one that is specified by the user
					handleNetworkAddressTranslation = ArgumentFunctions.mustParseBoolean(keyHandleNetworkAddressTranslation, map);
				}
			}else{
				netfilterHooksLogCT = false;
				netfilterHooksUser = false;
				handleNetworkAddressTranslation = false;
			}

			/*
			 * If we are live and local endpoints was true and harden was true then get the
			 * verify that the delete utility path is correct.
			 */
			if(harden){
				final String valueHardenProcesses = map.get(keyHardenProcesses);
				if(!HelperFunctions.isNullOrEmpty(valueHardenProcesses)){
					hardenProcesses.addAll(ArgumentFunctions.mustParseCommaSeparatedValues(keyHardenProcesses, map));
					for(final String hardenProcess : hardenProcesses){
						if(hardenProcess.trim().isEmpty()){
							throw new Exception("Process names to be hardened by the kernel module"
									+ " using the key '" + keyHardenProcesses + "' must not be empty");
						}
					}
				}
			}

			/*
			 * If we are live, and local endpoints was true then check if handle local
			 * endpoints is specified
			 */
			if(localEndpoints){
				final String valueHandleLocalEndpoints = map.get(keyHandleLocalEndpoints);
				if(HelperFunctions.isNullOrEmpty(valueHandleLocalEndpoints)){
					// If not specified then use the value of the local endpoints
					handleLocalEndpoints = localEndpoints;
				}else{
					// Use the one that is specified by the user
					handleLocalEndpoints = ArgumentFunctions.mustParseBoolean(keyHandleLocalEndpoints, map);
				}
			}else{
				// No need for it since local endpoints is false
				handleLocalEndpoints = false;
			}
		}else{
			/*
			 * We are not live i.e. log playback so we only care about handling of local
			 * endpoint records in the log
			 */
			kernelModuleMainPath = kernelModuleControllerPath = null;
			localEndpoints = false;
			harden = false;
			final String valueHandleLocalEndpoints = map.get(keyHandleLocalEndpoints);
			if(HelperFunctions.isNullOrEmpty(valueHandleLocalEndpoints)){
				// If not specified then set it to null
				handleLocalEndpoints = null;
			}else{
				handleLocalEndpoints = ArgumentFunctions.mustParseBoolean(keyHandleLocalEndpoints, map);
			}
			
			networkAddressTranslation = false;
			netfilterHooksLogCT = false;
			netfilterHooksUser = false;
			
			final String valueHandleNetworkAddressTranslation = map.get(keyHandleNetworkAddressTranslation);
			if(HelperFunctions.isNullOrEmpty(valueHandleNetworkAddressTranslation)){
				handleNetworkAddressTranslation = true;
			}else{
				handleNetworkAddressTranslation = ArgumentFunctions.mustParseBoolean(keyHandleNetworkAddressTranslation, map);
			}
		}

		return new KernelModuleConfiguration(kernelModuleMainPath, kernelModuleControllerPath,
				harden, localEndpoints, handleLocalEndpoints, hardenProcesses,
				networkAddressTranslation, netfilterHooksLogCT, netfilterHooksUser, handleNetworkAddressTranslation);
	}

	private static boolean isKernelModuleFileReadableIfExists(final String key, final String path) throws Exception{
		try{
			final File file = new File(path);
			if(!file.exists()){
				return false;
			}else{
				if(!file.isFile()){
					throw new Exception("Not a file");
				}else{
					if(!file.canRead()){
						throw new Exception("Not a readable file");
					}else{
						return true;
					}
				}
			}
		}catch(Exception e){
			throw new Exception("Failed check for if kernel module's path specified by '" + key
					+ "' is a readable file: '" + path + "'", e);
		}
	}

	public String getKernelModuleMainPath(){
		return kernelModuleMainPath;
	}

	public String getKernelModuleControllerPath(){
		return kernelModuleControllerPath;
	}

	public boolean isHarden(){
		return harden;
	}

	public boolean isLocalEndpoints(){
		return localEndpoints;
	}

	public Boolean isHandleLocalEndpoints(){
		return handleLocalEndpoints;
	}
	
	public void setHandleLocalEndpoints(final Boolean handleLocalEndpoints){
		this.handleLocalEndpoints = handleLocalEndpoints;
	}
	
	public boolean isHandleLocalEndpointsSpecified(){
		return handleLocalEndpoints != null;
	}
	
	public Set<String> getHardenProcesses(){
		return hardenProcesses;
	}
	
	public boolean isNetworkAddressTranslation(){
		return networkAddressTranslation;
	}
	
	public boolean isNetfilterHooksLogCT(){
		return netfilterHooksLogCT;
	}
	
	public boolean isNetfilterHooksUser(){
		return netfilterHooksUser;
	}
	
	public boolean isHandleNetworkAddressTranslation(){
		return handleNetworkAddressTranslation;
	}

	@Override
	public String toString(){
		return "KernelModuleConfiguration ["
				+ keyKernelModuleMainPath + "=" + kernelModuleMainPath
				+ ", " + keyKernelModuleControllerPath + "=" + kernelModuleControllerPath
				+ ", " + keyHarden + "=" + harden
				+ ", " + keyLocalEndpoints + "=" + localEndpoints
				+ ", " + keyHandleLocalEndpoints + "=" + handleLocalEndpoints
				+ ", " + keyHardenProcesses + "=" + hardenProcesses
				+ ", " + keyNetworkAddressTranslation + "=" + networkAddressTranslation
				+ ", " + keyHandleNetworkAddressTranslation + "=" + handleNetworkAddressTranslation
				+ ", " + keyNetfilterHooksLogCT + "=" + netfilterHooksLogCT
				+ ", " + keyNetfilterHooksUser + "=" + netfilterHooksUser
				+ "]";
	}
}
