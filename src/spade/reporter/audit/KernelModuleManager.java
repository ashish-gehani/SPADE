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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import spade.utility.Execute;
import spade.utility.HelperFunctions;

public class KernelModuleManager{
	
	public static void insertModules(
		final String kernelModuleMainPath, final String kernelModuleControllerPath,
		final KernelModuleArgument kmArg,
		final Consumer<String> outputConsumer
	) throws Exception{

		boolean kernelModuleControllerAdded = lsmod(kernelModuleControllerPath);
		if(!kernelModuleControllerAdded){
			boolean kernelModuleMainAdded = lsmod(kernelModuleMainPath);
			if(!kernelModuleMainAdded){
				insmod(kernelModuleMainPath, null);
			}

			String kernelModuleControllerArguments = kmArg.toModuleArgumentString();

			if(outputConsumer != null){
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
	
	public static void disableModule(
		final String kernelModuleControllerPath,
		final Consumer<String> outputConsumerReal, final BiConsumer<String, Throwable> errorConsumerReal
	) throws Exception{
		boolean kernelModuleControllerAdded = lsmod(kernelModuleControllerPath);
		if(kernelModuleControllerAdded){
			rmmod(kernelModuleControllerPath, outputConsumerReal);
		}
	}
}