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

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import spade.reporter.audit.ProcessUserSyscallFilter.UserMode;
import spade.utility.Execute;
import spade.utility.HelperFunctions;

public class KernelModuleManager{
	
	public static void insertModules(final String kernelModuleMainPath, final String kernelModuleControllerPath,
			final String userId, final UserMode userMode, 
			final Set<String> pidsToIgnore, final Set<String> ppidsToIgnore,
			final boolean hookSendRecv, final boolean namespaces,
			final boolean networkAddressTranslation, final boolean netfilterHooksLogCt, final boolean netfilterHooksUser,
			final boolean harden, final Set<String> tgidsToHarden,
			final Consumer<String> outputConsumer) throws Exception{

		boolean kernelModuleControllerAdded = lsmod(kernelModuleControllerPath);
		if(!kernelModuleControllerAdded){
			boolean kernelModuleMainAdded = lsmod(kernelModuleMainPath);
			if(!kernelModuleMainAdded){
				insmod(kernelModuleMainPath, null);
			}

			// controller module arguments construction
			String valuePidsToIgnore = "";
			for(String pidToIgnore : pidsToIgnore){
				valuePidsToIgnore += pidToIgnore + ",";
			}
			if(valuePidsToIgnore.length() > 0){
				valuePidsToIgnore = valuePidsToIgnore.substring(0, valuePidsToIgnore.length() - 1);
			}

			String valuePpidsToIgnore = "";
			for(String ppidToIgnore : ppidsToIgnore){
				valuePpidsToIgnore += ppidToIgnore + ",";
			}
			if(valuePpidsToIgnore.length() > 0){
				valuePpidsToIgnore = valuePpidsToIgnore.substring(0, valuePpidsToIgnore.length() - 1);
			}

			String valueIgnoreUids = null;
			switch(userMode){
			case CAPTURE:
				valueIgnoreUids = "0";
				break;
			case IGNORE:
				valueIgnoreUids = "1";
				break;
			default:
				throw new Exception("Unexpected value for user mode: " + userMode);
			}

			String kernelModuleControllerArguments = " uids=\"" + userId + "\"" + " ignore_uids=\"" + valueIgnoreUids
					+ "\"" + " syscall_success=\"" + 1 + "\"" + " pids_ignore=\"" + valuePidsToIgnore + "\""
					+ " ppids_ignore=\"" + valuePpidsToIgnore + "\"" + " net_io=\"" + (hookSendRecv ? 1 : 0) + "\""
					+ " namespaces=\"" + (namespaces ? 1 : 0) + "\"" + " nf_hooks=\""
					+ (networkAddressTranslation ? 1 : 0) + "\"" + " nf_hooks_log_all_ct=\""
					+ (netfilterHooksLogCt ? 1 : 0) + "\"" + " nf_handle_user=\"" + (netfilterHooksUser ? 1 : 0) + "\"";

			String valueKernelModuleKey = null;
			String valueHardenTgids = null;
			if(harden){
				if(tgidsToHarden != null && !tgidsToHarden.isEmpty()){
					valueHardenTgids = "";
					for(String tgidToHarden : tgidsToHarden){
						tgidToHarden = tgidToHarden.trim();
						valueHardenTgids += tgidToHarden + ",";
					}
					if(valueHardenTgids.length() > 0){
						valueHardenTgids = valueHardenTgids.substring(0, valueHardenTgids.length() - 1);
						kernelModuleControllerArguments += " harden_tgids=\"" + valueHardenTgids + "\"";
					}
				}
			}

			if(outputConsumer != null){
				// Print before appending the key. NOTE!!!
				outputConsumer.accept("Controller kernel module arguments: [" + kernelModuleControllerArguments + "]");
			}

			try{
				insmod(kernelModuleControllerPath, kernelModuleControllerArguments);
			}catch(Exception e){
				throw e;
			}
		}
	}
	
	public static void insmod(String kernelModulePath, String kernelModuleArguments) throws Exception{
		try{
			String command = "insmod " + kernelModulePath;
			if(!HelperFunctions.isNullOrEmpty(kernelModuleArguments)){
				command += " " + kernelModuleArguments.trim();
			}
			Execute.Output output = Execute.getOutput(command);
			if(!output.getStdErr().isEmpty()){
				throw new Exception("Failed to insert kernel module '" + kernelModulePath + "': " + output.getStdErr());
			}
		}catch(Exception e){
			throw new Exception("Failed to insert kernel module '" + kernelModulePath + "'", e);
		}
	}
	
	private static String getKernelModuleNameFromPath(final String kernelModulePath){
		String tokens[] = kernelModulePath.split("/");
		String name = tokens[tokens.length - 1];
		tokens = name.split("\\.");
		return tokens[0];
	}
	
	public static boolean lsmod(String kernelModulePath) throws Exception{
		try{
			String kernelModuleName = getKernelModuleNameFromPath(kernelModulePath);

			Execute.Output output = Execute.getOutput("lsmod");
			if(output.hasError()){
				throw new Exception("Failed to check if kernel module '" + kernelModulePath + "' exists: " + output.getStdErr());
			}else{
				List<String> stdOutLines = output.getStdOut();
				for(String line : stdOutLines){
					String[] resultTokens = line.split("\\s+");
					if(resultTokens[0].equals(kernelModuleName)){
						return true;
					}
				}
				return false;
			}
		}catch(Exception e){
			throw new Exception("Failed to check if kernel module '" + kernelModulePath + "' exists", e);
		}
	}
	
	public static void rmmod(String kernelModulePath, final Consumer<String> outputConsumerReal) throws Exception{
		try{
			String kernelModuleName = getKernelModuleNameFromPath(kernelModulePath);
			String command = "rmmod " + kernelModuleName;
			Execute.Output output = Execute.getOutput(command);
			if(output.hasError()){
				throw new Exception("Failed to remove kernel module '" + kernelModulePath + "': " + output.getStdErr());
			}
			if(outputConsumerReal != null){
				outputConsumerReal.accept("Successfully removed kernel module using '" + "rmmod" + "'");
			}
		}catch(Exception e){
			throw new Exception("Failed to remove kernel module '" + kernelModulePath + "'", e);
		}
	}
	
	public static void disableModule(final String kernelModuleControllerPath, 
			final boolean harden,
			final Consumer<String> outputConsumerReal, final BiConsumer<String, Throwable> errorConsumerReal) throws Exception{
		boolean kernelModuleControllerAdded = lsmod(kernelModuleControllerPath);
		if(kernelModuleControllerAdded){
			rmmod(kernelModuleControllerPath, outputConsumerReal);
		}
	}
}