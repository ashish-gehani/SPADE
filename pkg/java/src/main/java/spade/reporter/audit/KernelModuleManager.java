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
		insertModules(kernelModuleMainPath, kernelModuleControllerPath, kmArg, outputConsumer, outputConsumer);
	}

	/**
	 * As above; warningConsumer gets the warning that the loaded main module is another build than
	 * the file. The main module stays loaded once inserted and is never removed here: removing it
	 * crashes every thread that is in one of its hooked system calls (one blocked in accept or recv
	 * returns into the freed module), so a rebuilt module takes effect only after a reboot.
	 */
	public static void insertModules(
		final String kernelModuleMainPath, final String kernelModuleControllerPath,
		final KernelModuleArgument kmArg,
		final Consumer<String> outputConsumer, final Consumer<String> warningConsumer
	) throws Exception{

		boolean kernelModuleControllerAdded = lsmod(kernelModuleControllerPath);
		if(!kernelModuleControllerAdded){
			boolean kernelModuleMainAdded = lsmod(kernelModuleMainPath);
			if(!kernelModuleMainAdded){
				insmod(kernelModuleMainPath, null);
			}else if(warningConsumer != null){
				final String warning = staleModuleWarning(kernelModuleMainPath,
						loadedSrcversion(kernelModuleMainPath), fileSrcversion(kernelModuleMainPath));
				if(warning != null){
					warningConsumer.accept(warning);
				}
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

	/**
	 * The warning that the loaded module is another build than the file, or null when they are the
	 * same or either srcversion is unknown.
	 */
	static String staleModuleWarning(final String kernelModulePath, final String loaded, final String file){
		if(loaded == null || file == null || loaded.isEmpty() || file.isEmpty() || loaded.equals(file)){
			return null;
		}
		String name;
		try{
			name = getKernelModuleNameFromPath(kernelModulePath);
		}catch(Exception e){
			name = kernelModulePath;
		}
		return "The loaded kernel module '" + name + "' is another build than '" + kernelModulePath
				+ "' (srcversion " + loaded + ", the file's " + file + "), and the reporter records with"
				+ " the loaded one: a rebuilt module has no effect until it is loaded. Do not remove it with"
				+ " rmmod while processes run: a thread that is in one of its hooked system calls when it is"
				+ " removed crashes when the call returns. Reboot to load the rebuilt module.";
	}

	/** The srcversion of the loaded module of this file's name, or null. */
	static String loadedSrcversion(final String kernelModulePath){
		try{
			final String name = getKernelModuleNameFromPath(kernelModulePath);
			return new String(java.nio.file.Files.readAllBytes(
					java.nio.file.Paths.get("/sys/module/" + name + "/srcversion"))).trim();
		}catch(Exception e){
			return null;
		}
	}

	/** The srcversion of the module file, or null. */
	static String fileSrcversion(final String kernelModulePath){
		try{
			final Execute.Output output = Execute.getOutput(new String[]{"modinfo", "-F", "srcversion", kernelModulePath});
			final List<String> lines = output.getStdOut();
			if(output.hasError() || lines == null || lines.isEmpty()){
				return null;
			}
			return lines.get(0).trim();
		}catch(Exception e){
			return null;
		}
	}
}