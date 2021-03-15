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
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.Result;

public class KernelModuleConfiguration{

	public static final String keyKernelModuleMainPath = "kernelModuleMain",
			keyKernelModuleControllerPath = "kernelModuleController",
			keyKernelModuleDeleteBinaryPath = "kernelModuleDeleteBinary", keyHarden = "harden",
			keyLocalEndpoints = "localEndpoints", keyHandleLocalEndpoints = "handleLocalEndpoints",
			keyHardenProcesses = "hardenProcesses";

	private String kernelModuleMainPath, kernelModuleControllerPath, kernelModuleDeleteBinaryPath;
	private boolean harden, localEndpoints;
	private Boolean handleLocalEndpoints;
	private Set<String> hardenProcesses = new HashSet<String>();

	private KernelModuleConfiguration(final String kernelModuleMainPath, final String kernelModuleControllerPath,
			final String kernelModuleDeleteBinaryPath, final boolean harden, final boolean localEndpoints,
			final Boolean handleLocalEndpoints, final Set<String> hardenProcesses){
		this.kernelModuleMainPath = kernelModuleMainPath;
		this.kernelModuleControllerPath = kernelModuleControllerPath;
		this.kernelModuleDeleteBinaryPath = kernelModuleDeleteBinaryPath;
		this.harden = harden;
		this.localEndpoints = localEndpoints;
		this.handleLocalEndpoints = handleLocalEndpoints;
		this.hardenProcesses.addAll(hardenProcesses);
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
		String valueKernelModuleDeleteBinaryPath = map.get(keyKernelModuleDeleteBinaryPath);
		final String valueHarden = map.get(keyHarden);
		final String valueLocalEndpoints = map.get(keyLocalEndpoints);
		final String valueHandleLocalEndpoints = map.get(keyHandleLocalEndpoints);
		final String valueHardenProcesses = map.get(keyHardenProcesses);

		final String kernelModuleMainPath;
		final String kernelModuleControllerPath;
		final String kernelModuleDeleteBinaryPath;
		final boolean localEndpoints;
		final boolean harden;
		final Boolean handleLocalEndpoints;
		final Set<String> hardenProcesses = new HashSet<String>();

		if(isLive){
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
				final Result<Boolean> resultLocalEndpoints = HelperFunctions.parseBoolean(valueLocalEndpoints);
				if(resultLocalEndpoints.error){
					throw new Exception("Invalid value for key '" + keyLocalEndpoints + "'. "
							+ resultLocalEndpoints.toErrorString());
				}
				localEndpoints = resultLocalEndpoints.result;
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
				final Result<Boolean> resultHarden = HelperFunctions.parseBoolean(valueHarden);
				if(resultHarden.error){
					throw new Exception("Invalid value for key '" + keyHarden + "'. " + resultHarden.toErrorString());
				}
				harden = resultHarden.result;
			}else{
				harden = false;
			}

			/*
			 * If we are live and local endpoints was true and harden was true then get the
			 * verify that the delete utility path is correct.
			 */
			if(harden){
				valueKernelModuleDeleteBinaryPath = Settings.getPathRelativeToSPADERootIfNotAbsolute(valueKernelModuleDeleteBinaryPath);
				try{
					FileUtility.pathMustBeAReadableExecutableFile(valueKernelModuleDeleteBinaryPath);
					kernelModuleDeleteBinaryPath = valueKernelModuleDeleteBinaryPath;
				}catch(Exception e){
					throw new Exception("The kernel module deletion utility specified by '"
							+ keyKernelModuleDeleteBinaryPath + "' must be a readable and executable file: '"
							+ valueKernelModuleDeleteBinaryPath + "'", e);
				}
				if(!HelperFunctions.isNullOrEmpty(valueHardenProcesses)){
					final String[] tokens = valueHardenProcesses.split(",");
					for(final String token : tokens){
						if(token.trim().isEmpty()){
							throw new Exception("Process names to be hardened by the kernel module"
									+ " using the key '" + keyHardenProcesses + "' must not be empty");
						}else{
							hardenProcesses.add(token.trim());
						}
					}
				}
			}else{
				kernelModuleDeleteBinaryPath = null;
			}

			/*
			 * If we are live, and local endpoints was true then check if handle local
			 * endpoints is specified
			 */
			if(localEndpoints){
				if(HelperFunctions.isNullOrEmpty(valueHandleLocalEndpoints)){
					// If not specified then use the value of the local endpoints
					handleLocalEndpoints = localEndpoints;
				}else{
					// Use the one that is specified by the user
					handleLocalEndpoints = mustParseHandleLocalEndpoints(valueHandleLocalEndpoints);
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
			kernelModuleMainPath = kernelModuleControllerPath = kernelModuleDeleteBinaryPath = null;
			localEndpoints = false;
			harden = false;
			if(HelperFunctions.isNullOrEmpty(valueHandleLocalEndpoints)){
				// If not specified then set it to null
				handleLocalEndpoints = null;
			}else{
				handleLocalEndpoints = mustParseHandleLocalEndpoints(valueHandleLocalEndpoints);
			}
		}

		return new KernelModuleConfiguration(kernelModuleMainPath, kernelModuleControllerPath,
				kernelModuleDeleteBinaryPath, harden, localEndpoints, handleLocalEndpoints, hardenProcesses);
	}

	private static boolean mustParseHandleLocalEndpoints(final String value) throws Exception{
		final Result<Boolean> result = HelperFunctions.parseBoolean(value);
		if(result.error){
			throw new Exception("Invalid value for key '" + keyHandleLocalEndpoints + "'. " + result.toErrorString());
		}
		return result.result;
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

	public String getKernelModuleDeleteBinaryPath(){
		return kernelModuleDeleteBinaryPath;
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

	@Override
	public String toString(){
		return "KernelModuleConfiguration [" + keyKernelModuleMainPath + "=" + kernelModuleMainPath + ", "
				+ keyKernelModuleControllerPath + "=" + kernelModuleControllerPath + ", "
				+ keyKernelModuleDeleteBinaryPath + "=" + kernelModuleDeleteBinaryPath + ", " + keyHarden + "=" + harden
				+ ", " + keyLocalEndpoints + "=" + localEndpoints + ", " + keyHandleLocalEndpoints + "="
				+ handleLocalEndpoints + ", " +keyHardenProcesses+ "=" + hardenProcesses + "]";
	}
}
