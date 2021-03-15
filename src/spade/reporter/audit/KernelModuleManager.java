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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.commons.codec.digest.DigestUtils;

import spade.reporter.audit.ProcessUserSyscallFilter.UserMode;
import spade.utility.Execute;
import spade.utility.HelperFunctions;

public class KernelModuleManager{

	private static final StringBuffer kernelModuleSecretKey = new StringBuffer();
	
	public static void insertModules(final String kernelModuleMainPath, final String kernelModuleControllerPath,
			final String userId, final UserMode userMode, 
			final Set<String> pidsToIgnore, final Set<String> ppidsToIgnore,
			final boolean hookSendRecv, final boolean namespaces,
			final boolean netfilterHooks, final boolean netfilterHooksLogCt, final boolean netfilterHooksUser,
			final boolean harden, final Set<String> tgidsToHarden,
			final Consumer<String> outputConsumer) throws Exception{

		synchronized(kernelModuleSecretKey){
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
					case CAPTURE: valueIgnoreUids = "0"; break;
					case IGNORE: valueIgnoreUids = "1"; break;
					default: throw new Exception("Unexpected value for user mode: " + userMode);
				}

				String kernelModuleControllerArguments = 
						" uids=\"" + userId + "\""
						+ " ignore_uids=\"" + valueIgnoreUids + "\""
						+ " syscall_success=\"" + 1 + "\""
						+ " pids_ignore=\"" + valuePidsToIgnore + "\""
						+ " ppids_ignore=\"" + valuePpidsToIgnore + "\""
						+ " net_io=\"" + (hookSendRecv ? 1 : 0) + "\""
						+ " namespaces=\"" + (namespaces ? 1 : 0) + "\""
						+ " nf_hooks=\"" + (netfilterHooks ? 1 : 0) + "\""
						+ " nf_hooks_log_all_ct=\"" + (netfilterHooksLogCt ? 1 : 0) + "\""
						+ " nf_handle_user=\"" + (netfilterHooksUser ? 1 : 0) + "\"";

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
							kernelModuleControllerArguments += " harden_tgids=\""+valueHardenTgids+"\"";
						}
					}
				}

				if(outputConsumer != null){
					// Print before appending the key. NOTE!!!
					outputConsumer.accept("Controller kernel module arguments: [" + kernelModuleControllerArguments + "]");
				}

				if(harden){
					String dataKernelModuleKey = String.valueOf(System.nanoTime()) + "&" + String.valueOf(Math.random());
					valueKernelModuleKey = DigestUtils.md5Hex(dataKernelModuleKey);
					if(valueKernelModuleKey.length() >= 40){
						throw new Exception("The kernel module's secret key's length must be less than 40 but is: " + valueKernelModuleKey.length());
					}
					kernelModuleControllerArguments += " key=\"" + valueKernelModuleKey + "\"";
				}

				try{
					insmod(kernelModuleControllerPath, kernelModuleControllerArguments);
					kernelModuleSecretKey.setLength(0);
					if(valueKernelModuleKey != null){
						kernelModuleSecretKey.append(valueKernelModuleKey);
					}
				}catch(Exception e){
					throw e;
				}
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

	public static void deleteModule(String deleteModuleBinaryPath, 
			Consumer<String> outputConsumerReal, BiConsumer<String, Throwable> errorConsumerReal) throws Exception{
		final Consumer<String> outputConsumer;
		if(outputConsumerReal == null){
			outputConsumer = new Consumer<String>(){
				@Override public void accept(String t){ }
			};
		}else{
			outputConsumer = outputConsumerReal;
		}
		final BiConsumer<String, Throwable> errorConsumer;
		if(errorConsumerReal == null){
			errorConsumer = new BiConsumer<String, Throwable>(){
				@Override public void accept(String t, Throwable u){ }
			};
		}else{
			errorConsumer = errorConsumerReal;
		}
		synchronized(kernelModuleSecretKey){
			if(kernelModuleSecretKey.length() > 0){
				try{
					final java.lang.Process process = Runtime.getRuntime().exec(deleteModuleBinaryPath);
					Thread stdoutReaderThread = new Thread(new Runnable(){
						public void run(){
							try{
								InputStream stdout = process.getInputStream();
								BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
								String line = null;
								while((line = br.readLine()) != null){
									outputConsumer.accept("[ " + deleteModuleBinaryPath + " ] [STDOUT] " + line);
								}
							}catch(Exception e){
								errorConsumer.accept("Failed to read STDOUT of process '" + deleteModuleBinaryPath + "'", e);
							}
						}
					});

					Thread stderrReaderThread = new Thread(new Runnable(){
						public void run(){
							try{
								InputStream stderr = process.getErrorStream();
								BufferedReader br = new BufferedReader(new InputStreamReader(stderr));
								String line = null;
								while((line = br.readLine()) != null){
									errorConsumer.accept("[ " + deleteModuleBinaryPath + " ] [STDERR] " + line, null);
								}
							}catch(Exception e){
								errorConsumer.accept("Failed to read STDERR of process '" + deleteModuleBinaryPath + "'", e);
							}
						}
					});

					try{
						stdoutReaderThread.start();
						stderrReaderThread.start();
					}catch(Exception e){
						errorConsumer.accept("Failed to read STDOUT/STDERR of process '" + deleteModuleBinaryPath + "'", e);
						return;
					}
					
					try{
						PrintWriter stdInWriter = new PrintWriter(process.getOutputStream());
						stdInWriter.println(kernelModuleSecretKey.toString());
						stdInWriter.flush();
						stdInWriter.close();
					}catch(Exception e){
						errorConsumer.accept("Failed to write to STDIN of process '" + deleteModuleBinaryPath + "'", e);
						return;
					}
					
					int resultValue = process.waitFor();
					if(resultValue == 0){ // success
						outputConsumer.accept("Successfully removed kernel module using '" + deleteModuleBinaryPath + "'");
						kernelModuleSecretKey.setLength(0);
					}else{
						errorConsumer.accept("Failed to remove kernel module using '" + deleteModuleBinaryPath + "'. Error: " + resultValue, null);
					}
				}catch(Exception e){
					errorConsumer.accept("Failed to successfully execute process '" + deleteModuleBinaryPath + "'", e);
				}
			}
		}
	}
	
	public static void disableModule(final String kernelModuleControllerPath, 
			final boolean harden, final String deleteModuleBinaryPath, 
			final Consumer<String> outputConsumerReal, final BiConsumer<String, Throwable> errorConsumerReal) throws Exception{
		boolean kernelModuleControllerAdded = lsmod(kernelModuleControllerPath);
		if(kernelModuleControllerAdded){
			if(!harden){
				rmmod(kernelModuleControllerPath, outputConsumerReal);
			}else{
				deleteModule(deleteModuleBinaryPath, outputConsumerReal, errorConsumerReal);
			}
		}
	}
}